/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2024 Multiview Reconstruction developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
package net.preibisch.mvrecon.process.deconvolution.iteration.sequential;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.preibisch.mvrecon.process.cuda.Block;
import net.preibisch.mvrecon.process.cuda.CUDADevice;
import net.preibisch.mvrecon.process.cuda.CUDAFourierConvolution;
import net.preibisch.mvrecon.process.cuda.CUDATools;
import net.preibisch.mvrecon.process.deconvolution.DeconView;
import net.preibisch.mvrecon.process.fusion.FusionTools;
import net.preibisch.mvrecon.process.fusion.ImagePortion;
import net.preibisch.mvrecon.process.interestpointdetection.methods.dog.DoGImgLib2;

public class ComputeBlockSeqThreadCUDA extends ComputeBlockSeqThreadAbstract
{
	final ExecutorService service;
	final ArrayList< Callable< Void > > tasks;
	final ArrayList< ImagePortion > portions;
	final ArrayImg< FloatType, ? > tmp1, tmp2;
	final float lambda;

	final CUDADevice cudaDevice;
	final CUDAFourierConvolution cuda;

	public ComputeBlockSeqThreadCUDA(
			final ExecutorService service,
			final float minValue,
			final float lambda,
			final int id,
			final int[] blockSize,
			final CUDAFourierConvolution cuda,
			final CUDADevice cudaDevice )
	{
		super( minValue, blockSize, id );

		this.cudaDevice = cudaDevice;
		this.cuda = cuda;
		this.tmp1 = new ArrayImgFactory< FloatType >().create( Util.int2long( blockSize ), new FloatType() );
		this.tmp2 = new ArrayImgFactory< FloatType >().create( Util.int2long( blockSize ), new FloatType() );
		this.service = service;
		this.tasks = new ArrayList<>();
		this.portions = new ArrayList<>();
		this.lambda = lambda;

		this.portions.addAll( FusionTools.divideIntoPortions( tmp1.size() ) );
	}

	@Override
	public IterationStatistics runIteration(
			final DeconView view,
			final Block block,
			final RandomAccessibleInterval< FloatType > imgBlock,
			final RandomAccessibleInterval< FloatType > weightBlock,
			final float maxIntensityView,
			final ArrayImg< FloatType, ? > kernel1,
			final ArrayImg< FloatType, ? > kernel2 )
	{
		//
		// convolve psi (current guess of the image) with the PSF of the current view
		// [psi >> tmp1]
		//
		convolve1( getPsiBlockTmp(), kernel1, tmp1 );

		//
		// compute quotient img/psiBlurred
		// [tmp1, img >> tmp1]
		//
		// outofbounds in the original image are already set to quotient==1 since there is no input image
		//
		tasks.clear();
		for ( final ImagePortion portion : portions )
		{
			tasks.add( new Callable< Void >()
			{
				@Override
				public Void call() throws Exception
				{
					DeconvolutionMethods.computeQuotient( portion.getStartPosition(), portion.getLoopSize(), tmp1, imgBlock );
					return null;
				}
			});
		}

		FusionTools.execTasks( tasks, service, "compute quotient" );

		//
		// blur the residuals image with the kernel
		// (this cannot be don in-place as it might be computed in blocks sequentially,
		// and the input for the n+1'th block cannot be formed by the written back output
		// of the n'th block)
		// [tmp1 >> tmp2]
		//
		convolve2( tmp1, kernel2, tmp2 );

		//
		// compute final values
		// [psi, weights, tmp2 >> psi]
		//
		final double[][] sumMax = new double[ portions.size() ][ 2 ];
		tasks.clear();

		for ( int i = 0; i < portions.size(); ++i )
		{
			final ImagePortion portion = portions.get( i );
			final int portionId = i;

			tasks.add( new Callable< Void >()
			{
				@Override
				public Void call() throws Exception
				{
					DeconvolutionMethods.computeFinalValues(
							portion.getStartPosition(),
							portion.getLoopSize(),
							getPsiBlockTmp(),
							tmp2,
							weightBlock,
							lambda,
							getMinValue(),
							maxIntensityView,
							sumMax[ portionId ] );
					return null;
				}
			});
		}

		FusionTools.execTasks( tasks, service, "compute final values " + view );

		// accumulate the results from the individual threads
		final IterationStatistics is = new IterationStatistics();

		for ( int i = 0; i < portions.size(); ++i )
		{
			is.sumChange += sumMax[ i ][ 0 ];
			is.maxChange = Math.max( is.maxChange, sumMax[ i ][ 1 ] );
		}

		return is;
	}

	public void convolve1(
			final RandomAccessibleInterval< FloatType > psi,
			final ArrayImg< FloatType, ? > kernel1,
			final ArrayImg< FloatType, ? > tmp1 )
	{
		// copy psi onto tmp1
		FusionTools.copyImg( psi, tmp1, service, false );

		final float[] blockF = ((FloatArray)(tmp1).update( null ) ).getCurrentStorageArray();
		final float[] kernel1F = ((FloatArray)(kernel1).update( null ) ).getCurrentStorageArray();

		// in-place CUDA convolution of tmp1 with kernel1 using CUDA
		long time = System.currentTimeMillis();
		cuda.convolution3DfftCUDAInPlace(
				blockF, CUDATools.getCUDACoordinates( DoGImgLib2.getImgSizeInt( tmp1 ) ),
				kernel1F, CUDATools.getCUDACoordinates( DoGImgLib2.getImgSizeInt( kernel1 ) ),
				cudaDevice.getDeviceId() );

		System.out.println( " block " + getId() + "(CUDA " + cudaDevice.getDeviceId() + "): compute " + (System.currentTimeMillis() - time) );
	}

	public void convolve2(
			final ArrayImg< FloatType, ? > tmp1,
			final ArrayImg< FloatType, ? > kernel2,
			final ArrayImg< FloatType, ? > tmp2 )
	{
		// copy tmp1 onto tmp2
		FusionTools.copyImg( tmp1, tmp2, service, false );

		final float[] blockF = ((FloatArray)(tmp2).update( null ) ).getCurrentStorageArray();
		final float[] kernel2F = ((FloatArray)(kernel2).update( null ) ).getCurrentStorageArray();

		// in-place CUDA convolution of tmp2 with kernel2 using CUDA
		cuda.convolution3DfftCUDAInPlace(
				blockF, CUDATools.getCUDACoordinates( DoGImgLib2.getImgSizeInt( tmp2 ) ),
				kernel2F, CUDATools.getCUDACoordinates( DoGImgLib2.getImgSizeInt( kernel2 ) ),
				cudaDevice.getDeviceId() );
	}
}
