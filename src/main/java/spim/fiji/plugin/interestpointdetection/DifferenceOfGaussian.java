package spim.fiji.plugin.interestpointdetection;

import ij.ImagePlus;
import ij.gui.GenericDialog;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

import mpicbg.imglib.image.Image;
import mpicbg.imglib.multithreading.SimpleMultiThreading;
import mpicbg.imglib.type.numeric.real.FloatType;
import mpicbg.imglib.wrapper.ImgLib2;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.segmentation.InteractiveDoG;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.process.interestpointdetection.ProcessDOG;

public class DifferenceOfGaussian extends DifferenceOf
{
	public static double defaultS = 1.8;
	public static double defaultT = 0.008;

	public static double defaultSigma[];
	public static double defaultThreshold[];
	public static boolean defaultFindMin[];
	public static boolean defaultFindMax[];

	double[] sigma;
	double[] threshold;
	boolean[] findMin;
	boolean[] findMax;	

	public DifferenceOfGaussian(
			final SpimData2 spimData,
			final List<Angle> anglesToProcess,
			final List<Channel> channelsToProcess,
			final List<Illumination> illumsToProcess,
			final List<TimePoint> timepointsToProcess )
	{
		super( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess );
	}

	@Override
	public String getDescription() { return "Difference-of-Gaussian"; }

	@Override
	public DifferenceOfGaussian newInstance(
			final SpimData2 spimData,
			final List<Angle> anglesToProcess,
			final List<Channel> channelsToProcess,
			final List<Illumination> illumsToProcess,
			final List<TimePoint> timepointsToProcess ) 
	{ 
		return new DifferenceOfGaussian( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess );
	}


	@Override
	public HashMap< ViewId, List< InterestPoint > > findInterestPoints( final TimePoint t )
	{
		final HashMap< ViewId, List< InterestPoint > > interestPoints = new HashMap< ViewId, List< InterestPoint > >();
		
		for ( final Angle a : anglesToProcess )
			for ( final Illumination i : illumsToProcess )
				for ( final Channel c : channelsToProcess )
				{
					// make sure not everything crashes if one file is missing
					try
					{
						//
						// open the corresponding image (if present at this timepoint)
						//
						long time1 = System.currentTimeMillis();
						final ViewId viewId = SpimData2.getViewId( spimData.getSequenceDescription(), t, c, a, i );

						if ( viewId == null )
						{
							IOFunctions.println( "An error occured. Count not find the corresponding ViewSetup for angle: " + 
								a.getId() + " channel: " + c.getId() + " illum: " + i.getId() );
						
							continue;
						}
						
						final ViewDescription viewDescription = spimData.getSequenceDescription().getViewDescription( 
								viewId.getTimePointId(), viewId.getViewSetupId() );

						if ( !viewDescription.isPresent() )
							continue;

						IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Requesting Img from ImgLoader (tp=" + viewId.getTimePointId() + ", setup=" + viewId.getViewSetupId() + ")" );
						final RandomAccessibleInterval< net.imglib2.type.numeric.real.FloatType > input = spimData.getSequenceDescription().getImgLoader().getFloatImage( viewId, false );
												
						long time2 = System.currentTimeMillis();
						
						benchmark.openFiles += time2 - time1;
						
						preSmooth( input );
						
						final Image< FloatType > img = ImgLib2.wrapFloatToImgLib1( (Img<net.imglib2.type.numeric.real.FloatType>)input );

						//
						// compute Difference-of-Mean
						//
						interestPoints.put(viewId, ProcessDOG.compute(
								img,
								(float)sigma[ c.getId() ],
								(float)threshold[ c.getId() ],
								localization,
								Math.min( imageSigmaX, (float)sigma[ c.getId() ] ),
								Math.min( imageSigmaY, (float)sigma[ c.getId() ] ),
								Math.min( imageSigmaZ, (float)sigma[ c.getId() ] ),
								findMin[ c.getId() ],
								findMax[ c.getId() ],
								minIntensity,
								maxIntensity ) );
						img.close();

				        benchmark.computation += System.currentTimeMillis() - time2;
					}
					catch ( Exception  e )
					{
						IOFunctions.println( "An error occured. Failed to segment angle: " + 
								a.getId() + " channel: " + c.getId() + " illum: " + i.getId() + ". Continuing with next one." );
						e.printStackTrace();
					}
				}

		return interestPoints;
	}

	@Override
	protected boolean setDefaultValues( final Channel channel, final int brightness )
	{
		final int channelId = channel.getId();
		
		this.sigma[ channelId ] = defaultS;
		this.findMin[ channelId ] = false;
		this.findMax[ channelId ] = true;

		if ( brightness == 0 )
			this.threshold[ channelId ] = 0.001;
		else if ( brightness == 1 )
			this.threshold[ channelId ] = 0.008;
		else if ( brightness == 2 )
			this.threshold[ channelId ] = 0.03;
		else if ( brightness == 3 )
			this.threshold[ channelId ] = 0.1;
		else
			return false;
		
		return true;
	}

	@Override
	protected boolean setAdvancedValues( final Channel channel )
	{
		final int channelId = channel.getId();
		
		final GenericDialog gd = new GenericDialog( "Advanced values for channel " + channel.getName() );
		
		gd.addMessage( "Advanced values for channel " + channel.getName() );
		gd.addNumericField( "Sigma", defaultSigma[ channelId ], 5 );
		gd.addNumericField( "Threshold", defaultThreshold[ channelId ], 4 );
		gd.addCheckbox( "Find_minima", defaultFindMin[ channelId ] );
		gd.addCheckbox( "Find_maxima", defaultFindMax[ channelId ] );

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return false;
		
		this.sigma[ channelId ] = defaultSigma[ channelId ] = gd.getNextNumber();
		this.threshold[ channelId ] = defaultThreshold[ channelId ] = gd.getNextNumber();
		this.findMin[ channelId ] = defaultFindMin[ channelId ] = gd.getNextBoolean();
		this.findMax[ channelId ] = defaultFindMax[ channelId ] = gd.getNextBoolean();
		
		return true;
	}

	@Override
	protected boolean setInteractiveValues( final Channel channel )
	{
		final ViewId view = getViewSelection( "Interactive Difference-of-Gaussian", "Please select view to use for channel " + channel.getName(), channel );
		
		if ( view == null )
			return false;
		
		final ViewDescription viewDescription = spimData.getSequenceDescription().getViewDescription( view.getTimePointId(), view.getViewSetupId() );
		
		if ( !viewDescription.isPresent() )
		{
			IOFunctions.println( "You defined the view you selected as not present at this timepoint." );
			IOFunctions.println( "timepoint: " + viewDescription.getTimePoint().getName() + 
								 " angle: " + viewDescription.getViewSetup().getAngle().getName() + 
								 " channel: " + viewDescription.getViewSetup().getChannel().getName() + 
								 " illum: " + viewDescription.getViewSetup().getIllumination().getName() );
			return false;
		}

		RandomAccessibleInterval< net.imglib2.type.numeric.real.FloatType > img = 
				spimData.getSequenceDescription().getImgLoader().getFloatImage( view, false );
				
		if ( img == null )
		{
			IOFunctions.println( "View not found: " + viewDescription );
			return false;
		}
	
		preSmooth( img );

		final ImagePlus imp = ImageJFunctions.wrapFloat( img, "" ).duplicate();
		img = null;
		imp.setDimensions( 1, imp.getStackSize(), 1 );
		imp.setTitle( "tp: " + viewDescription.getTimePoint().getName() + " viewSetup: " + viewDescription.getViewSetupId() );		
		imp.show();		
		imp.setSlice( imp.getStackSize() / 2 );
		imp.setRoi( 0, 0, imp.getWidth()/3, imp.getHeight()/3 );		

		final InteractiveDoG idog = new InteractiveDoG();
		final int channelId = channel.getId();

		idog.setSigma2isAdjustable( false );
		idog.setInitialSigma( (float)defaultSigma[ channelId ] );
		idog.setThreshold( (float)defaultThreshold[ channelId ] );
		idog.setLookForMinima( defaultFindMin[ channelId ] );
		idog.setLookForMaxima( defaultFindMax[ channelId ] );

		idog.run( null );
		
		while ( !idog.isFinished() )
			SimpleMultiThreading.threadWait( 100 );
		
		imp.close();

		this.sigma[ channelId ] = defaultSigma[ channelId ] = idog.getInitialSigma();
		this.threshold[ channelId ] = defaultThreshold[ channelId ] = idog.getThreshold();
		this.findMin[ channelId ] = defaultFindMin[ channelId ] = idog.getLookForMinima();
		this.findMax[ channelId ] = defaultFindMax[ channelId ] = idog.getLookForMaxima();
		
		return true;
	}
	
	/**
	 * This is only necessary to make static objects so that the ImageJ dialog remembers choices
	 * for the right channel
	 * 
	 * @param numChannels - the TOTAL number of channels (not only the ones to process)
	 */
	@Override
	protected void init( final int numChannels )
	{
		this.sigma = new double[ numChannels ];
		this.threshold = new double[ numChannels ];
		this.findMin = new boolean[ numChannels ];
		this.findMax = new boolean[ numChannels ];

		if ( defaultSigma == null || defaultSigma.length != numChannels )
		{
			defaultSigma = new double[ numChannels ];
			defaultThreshold = new double[ numChannels ];
			defaultFindMin = new boolean[ numChannels ];
			defaultFindMax = new boolean[ numChannels ];
			
			for ( int c = 0; c < numChannels; ++c )
			{
				defaultSigma[ c ] = defaultS;
				defaultThreshold[ c ] = defaultT;
				defaultFindMin[ c ] = false;
				defaultFindMax[ c ] = true;
			}
		}
	}

	@Override
	public String getParameters( final int channelId )
	{
		return "DOG s=" + sigma[ channelId ] + " t=" + threshold[ channelId ] + " min=" + findMin[ channelId ] + " max=" + findMax[ channelId ] + 
				" imageSigmaX=" + imageSigmaX + " imageSigmaY=" + imageSigmaY + " imageSigmaZ=" + imageSigmaZ;
	}
}
