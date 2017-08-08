package spim.process.deconvolution.iteration;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.RealSum;
import spim.Threads;
import spim.fiji.ImgLib2Temp.Triple;
import spim.process.deconvolution.DeconView;
import spim.process.fusion.FusionTools;
import spim.process.fusion.ImagePortion;

public class PsiInitializationAvgPrecise implements PsiInitialization
{
	double avg = -1;
	float[] max = null;

	@Override
	public boolean runInitialization( final Img< FloatType > psi, final List< DeconView > views, final ExecutorService service )
	{
		this.max = new float[ views.size() ];

		final int nThreads = Threads.numThreads();
		final int nPortions = nThreads * 20;

		// split up into many parts for multithreading
		final Vector< ImagePortion > portions = FusionTools.divideIntoPortions( psi.size(), nPortions );
		final ArrayList< Callable< Triple< RealSum, Long, float[] > > > tasks = new ArrayList< Callable< Triple< RealSum, Long, float[] > > >();

		final ArrayList< RandomAccessibleInterval< FloatType > > imgs = new ArrayList< RandomAccessibleInterval< FloatType > >();

		for ( final DeconView mvdecon : views )
			imgs.add( mvdecon.getImage() );

		for ( final ImagePortion portion : portions )
			tasks.add( new PsiInitializationAvgPreciseThread( portion, psi, imgs ) );

		final RealSum s = new RealSum();
		long count = 0;

		try
		{
			// invokeAll() returns when all tasks are complete
			final List< Future< Triple< RealSum, Long, float[] > > > imgIntensities = service.invokeAll( tasks );

			for ( final Future< Triple< RealSum, Long, float[] >  > future : imgIntensities )
			{
				s.add( future.get().getA().getSum() );
				count += future.get().getB().longValue();

				if ( max != null )
					for ( int i = 0; i < max.length; ++i )
						max[ i ] = Math.max( max[ i ], future.get().getC()[ i ] );
			}
		}
		catch ( final Exception e )
		{
			IOFunctions.println( "Failed to fuse initial iteration: " + e );
			e.printStackTrace();
			return false;
		}

		avg = s.getSum() / (double)count;

		if ( Double.isNaN( avg ) )
		{
			avg = 1.0;
			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): ERROR! Computing average FAILED, is NaN, setting it to: " + avg );
		}

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Average intensity in overlapping area: " + avg );

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Setting image to average intensity: " + avg );

		for ( final FloatType t : psi )
			t.set( (float)avg );

		return true;
	}

	@Override
	public double getAvg() { return avg; }

	@Override
	public float[] getMax() { return max; }
}
