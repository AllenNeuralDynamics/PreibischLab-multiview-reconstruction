package spim.process.deconvolution2.iteration;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.type.numeric.complex.ComplexFloatType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import spim.Threads;
import spim.process.cuda.Block;
import spim.process.deconvolution2.DeconView;
import spim.process.deconvolution2.FFTConvolution;
import spim.process.fusion.FusionTools;
import spim.process.fusion.ImagePortion;

public class ComputeBlockThreadCPU extends ComputeBlockThreadAbstract
{
	final ExecutorService service;
	final ArrayList< Callable< Void > > tasks;
	final ArrayList< ImagePortion > portions;
	final ImgFactory< ComplexFloatType > fftFactory;
	final Img< FloatType > tmp1, tmp2;
	final float lambda;

	public ComputeBlockThreadCPU(
			final ExecutorService service,
			final float minValue,
			final float lambda,
			final int id,
			final int[] blockSize,
			final ImgFactory< FloatType > blockFactory )
	{
		super( blockFactory, minValue, blockSize, id );

		this.tmp1 = blockFactory.create( blockSize, new FloatType() );
		this.tmp2 = blockFactory.create( blockSize, new FloatType() );
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
		try { this.fftFactory = blockFactory.imgFactory( new ComplexFloatType() ); } catch ( IncompatibleTypeException e )
		{
			e.printStackTrace();
			throw new RuntimeException( "Cannot transform ImgFactory to ComplexFloatType." );
		}
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
		convolve1( psiBlockTmp, kernel1, view.getPSF().getKernel1FFT(), tmp1 );

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
		convolve2( tmp1, kernel2, view.getPSF().getKernel2FFT(), tmp2 );

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
			final RandomAccessibleInterval< FloatType > image,
			final Img< FloatType > kernel,
			final Img< ComplexFloatType > kernelFFT,
			final Img< FloatType > result )
	{
		final FFTConvolution< FloatType > fftConvolution =
				new FFTConvolution< FloatType >(
						Views.extendMirrorSingle( image ),
						image,
						Views.extendZero( kernel ),
						kernel,
						result,
						fftFactory );
		fftConvolution.setExecutorService( service );
		fftConvolution.setKeepImgFFT( false );
		fftConvolution.setKernelFFT( kernelFFT );
		fftConvolution.convolve();
	}

	public void convolve2(
			final RandomAccessibleInterval< FloatType > image,
			final Img< FloatType > kernel,
			final Img< ComplexFloatType > kernelFFT,
			final Img< FloatType > result )
	{
		final FFTConvolution< FloatType > fftConvolution =
				new FFTConvolution< FloatType >(
						Views.extendValue( image, new FloatType( 1.0f ) ), // ratio outside of the deconvolved space (psi) is 1, shouldn't matter here though
						image,
						Views.extendZero( kernel ),
						kernel,
						result,
						fftFactory );
		fftConvolution.setExecutorService( service );
		fftConvolution.setKeepImgFFT( false );
		fftConvolution.setKernelFFT( kernelFFT );
		fftConvolution.convolve();
	}

}
