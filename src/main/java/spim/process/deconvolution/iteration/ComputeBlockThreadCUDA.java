package spim.process.deconvolution.iteration;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import spim.Threads;
import spim.process.cuda.Block;
import spim.process.cuda.CUDADevice;
import spim.process.cuda.CUDAFourierConvolution;
import spim.process.cuda.CUDATools;
import spim.process.deconvolution.DeconView;
import spim.process.fusion.FusionTools;
import spim.process.fusion.ImagePortion;
import spim.process.interestpointdetection.methods.dog.DifferenceOfGaussianCUDA.CUDAOutput;

public class ComputeBlockThreadCUDA extends ComputeBlockThreadAbstract
{
	final ExecutorService service;
	final ArrayList< Callable< Void > > tasks;
	final ArrayList< ImagePortion > portions;
	final ArrayImg< FloatType, ? > tmp1, tmp2;
	final float lambda;

	final CUDADevice cudaDevice;
	final CUDAFourierConvolution cuda;

	public ComputeBlockThreadCUDA(
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

		final int numThreads;
		if ( ThreadPoolExecutor.class.isInstance( service ) )
			numThreads = ((ThreadPoolExecutor)service).getMaximumPoolSize();
		else
			numThreads = Threads.numThreads();

		this.portions.addAll( FusionTools.divideIntoPortions( tmp1.size(), numThreads * 2 ) );
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
		convolve1( psiBlockTmp, kernel1, tmp1 );

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
							psiBlockTmp,
							tmp2,
							weightBlock,
							lambda,
							minValue,
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
		FusionTools.copyImg( psi, tmp1, false, service );

		final float[] blockF = ((FloatArray)(tmp1).update( null ) ).getCurrentStorageArray();
		final float[] kernel1F = ((FloatArray)(kernel1).update( null ) ).getCurrentStorageArray();

		// in-place CUDA convolution of tmp1 with kernel1 using CUDA
		long time = System.currentTimeMillis();
		cuda.convolution3DfftCUDAInPlace(
				blockF, CUDATools.getCUDACoordinates( CUDAOutput.getImgSizeInt( tmp1 ) ),
				kernel1F, CUDATools.getCUDACoordinates( CUDAOutput.getImgSizeInt( kernel1 ) ),
				cudaDevice.getDeviceId() );

		System.out.println( " block " + id + "(CUDA " + cudaDevice.getDeviceId() + "): compute " + (System.currentTimeMillis() - time) );
	}

	public void convolve2(
			final ArrayImg< FloatType, ? > tmp1,
			final ArrayImg< FloatType, ? > kernel2,
			final ArrayImg< FloatType, ? > tmp2 )
	{
		// copy tmp1 onto tmp2
		FusionTools.copyImg( tmp1, tmp2, false, service );

		final float[] blockF = ((FloatArray)(tmp2).update( null ) ).getCurrentStorageArray();
		final float[] kernel2F = ((FloatArray)(kernel2).update( null ) ).getCurrentStorageArray();

		// in-place CUDA convolution of tmp2 with kernel2 using CUDA
		cuda.convolution3DfftCUDAInPlace(
				blockF, CUDATools.getCUDACoordinates( CUDAOutput.getImgSizeInt( tmp2 ) ),
				kernel2F, CUDATools.getCUDACoordinates( CUDAOutput.getImgSizeInt( kernel2 ) ),
				cudaDevice.getDeviceId() );
	}
}
