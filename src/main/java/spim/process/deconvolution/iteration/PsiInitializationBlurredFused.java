package spim.process.deconvolution.iteration;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;

import mpicbg.imglib.util.Util;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.RealSum;
import net.imglib2.view.Views;
import spim.process.deconvolution.DeconView;
import spim.process.deconvolution.util.FusedNonZeroRandomAccess;
import spim.process.deconvolution.util.FusedNonZeroRandomAccessibleInterval;
import spim.process.fusion.FusionTools;

public class PsiInitializationBlurredFused implements PsiInitialization
{
	final double sigma;

	double avg = -1;
	float[] max = null;

	public PsiInitializationBlurredFused( final double sigma )
	{
		this.sigma = sigma;
	}

	public PsiInitializationBlurredFused()
	{
		this( 5.0 );
	}

	@Override
	public boolean runInitialization( final Img< FloatType > psi, final List< DeconView > views, final ExecutorService service )
	{
		final ArrayList< RandomAccessibleInterval< FloatType > > images = new ArrayList<>();
		final ArrayList< RandomAccessibleInterval< FloatType > > weights = new ArrayList<>();

		for ( final DeconView view : views )
		{
			images.add( view.getImage() );
			weights.add( view.getWeight() );
		}

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Setting virtually fused image" );

		final FusedNonZeroRandomAccessibleInterval fused = new FusedNonZeroRandomAccessibleInterval( new FinalInterval( psi ), images, weights );

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Fusing estimate of deconvolved image ..." );

		FusionTools.copyImg( fused, psi, true, service );

		final RealSum s = new RealSum();
		long count = 0;
		this.max = new float[ views.size() ];

		for ( final FusedNonZeroRandomAccess ra : fused.getAllAccesses() )
		{
			for ( int i = 0; i < max.length; ++i )
				max[ i ] = Math.max( max[ i ], ra.getMax()[ i ] );

			s.add( ra.getRealSum().getSum() );
			count += ra.numContributingPixels();
		}

		avg = s.getSum() / (double)count;

		if ( Double.isNaN( avg ) )
		{
			avg = 1.0;
			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): ERROR! Computing average FAILED, is NaN, setting it to: " + avg );
		}

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Average intensity in overlapping area: " + avg );

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Blurring input image with sigma = " + sigma + " to use as input");

		try
		{
			Gauss3.gauss( Util.getArrayFromValue( sigma, psi.numDimensions() ), Views.extendMirrorSingle( psi ), psi, service );
		}
		catch ( IncompatibleTypeException e )
		{
			e.printStackTrace();
			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): ERROR, Couldn't convolve image: " + e );
			return false;
		}

		//DisplayImage.getImagePlusInstance( psi, false, "psi", Double.NaN, Double.NaN ).show();

		return true;
	}

	@Override
	public double getAvg() { return avg; }

	@Override
	public float[] getMax() { return max; }
}
