package spim.fiji.plugin.fusion;

import java.awt.Choice;
import java.awt.Label;
import java.awt.TextField;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;

import ij.gui.GenericDialog;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import spim.fiji.plugin.util.GUIHelper;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.boundingbox.BoundingBox;
import spim.fiji.spimdata.pointspreadfunctions.PointSpreadFunction;
import spim.process.boundingbox.BoundingBoxTools;
import spim.process.cuda.CUDADevice;
import spim.process.cuda.CUDAFourierConvolution;
import spim.process.cuda.CUDATools;
import spim.process.cuda.NativeLibraryTools;
import spim.process.deconvolution.DeconViewPSF.PSFTYPE;
import spim.process.deconvolution.MultiViewDeconvolution;
import spim.process.deconvolution.iteration.ComputeBlockThreadCPUFactory;
import spim.process.deconvolution.iteration.ComputeBlockThreadCUDAFactory;
import spim.process.deconvolution.iteration.ComputeBlockThreadFactory;
import spim.process.export.AppendSpimData2HDF5;
import spim.process.export.DisplayImage;
import spim.process.export.ExportSpimData2HDF5;
import spim.process.export.ExportSpimData2TIFF;
import spim.process.export.ImgExport;
import spim.process.export.Save3dTIFF;
import spim.process.fusion.FusionTools;
import spim.process.fusion.FusionTools.ImgDataType;
import spim.process.fusion.transformed.TransformVirtual;
import spim.process.interestpointdetection.methods.downsampling.DownsampleTools;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class DeconvolutionGUI implements FusionExportInterface
{
	public final static ArrayList< ImgExport > staticImgExportAlgorithms = new ArrayList< ImgExport >();
	public final static String[] imgExportDescriptions;

	static
	{
		IOFunctions.printIJLog = true;

		staticImgExportAlgorithms.add( new DisplayImage() );
		staticImgExportAlgorithms.add( new Save3dTIFF( null ) );
		staticImgExportAlgorithms.add( new ExportSpimData2TIFF() );
		staticImgExportAlgorithms.add( new ExportSpimData2HDF5() );
		staticImgExportAlgorithms.add( new AppendSpimData2HDF5() );

		imgExportDescriptions = new String[ staticImgExportAlgorithms.size() ];

		for ( int i = 0; i < staticImgExportAlgorithms.size(); ++i )
			imgExportDescriptions[ i ] = staticImgExportAlgorithms.get( i ).getDescription();
	}

	public static String[] computationOnChoice = new String[]{
			"CPU (Java)",
			"GPU (Nvidia CUDA via JNA)" };

	public static String[] osemspeedupChoice = new String[]{
			"1 (balanced)",
			"minimal number of overlapping views",
			"average number of overlapping views",
			"specify manually" };

	public static String[] blocksChoice = new String[]{
			"in 256x256x256 blocks", 
			"in 512x512x512 blocks",
			"in 768x768x768 blocks",
			"in 1024x1024x1024 blocks",
			"specify maximal blocksize manually",
			"one block (??????x??????x??????) for the entire image" };

	public static String[] psfTypeString = new String[]{
		"Efficient Bayesian - Optimization II (very fast, imprecise)", 
		"Efficient Bayesian - Optimization I (fast, precise)", 
		"Efficient Bayesian (less fast, more precise)", 
		"Independent (slow, very precise)" };

	public static String[] splittingTypes = new String[]{
			"Each timepoint & channel",
			"Each timepoint, channel & illumination",
			"All views together",
			"Each view" };

	public static int defaultBB = 0;
	public static int defaultInputImgCacheType = 1;
	public static int defaultWeightCacheType = 1;
	public static double defaultDownsampling = 1.0;
	public static int defaultPSFType = 1;
	public static double defaultOsemSpeedup = 1;
	public static int defaultNumIterations = 10;
	public static boolean defaultDebugMode = false;
	public static int defaultDebugInterval = 1;
	public static boolean defaultUseTikhonovRegularization = true;
	public static double defaultLambda = 0.006;
	public static int defaultBlockSizeIndex = 1;
	public static int defaultBlockSizeX = 384, defaultBlockSizeY = 384, defaultBlockSizeZ = 384;
	public static int defaultCacheBlockSize = MultiViewDeconvolution.cellDim;
	public static int defaultCacheMaxNumBlocks = MultiViewDeconvolution.maxCacheSize;
	public static int defaultPsiCopyBlockSize = MultiViewDeconvolution.cellDim * 2;
	public static int defaultComputeOnIndex = 0;
	public static boolean defaultAdjustBlending = false;
	public static float defaultBlendingRange = MultiViewDeconvolution.defaultBlendingRange;
	public static float defaultBlendingBorder = MultiViewDeconvolution.defaultBlendingBorder;
	public static boolean defaultAdditionalSmoothBlending = MultiViewDeconvolution.additionalSmoothBlending;
	public static boolean defaultGroupTiles = true;
	public static boolean defaultGroupIllums = true;
	public static int defaultSplittingType = 0;
	public static int defaultImgExportAlgorithm = 0;


	protected int boundingBox = defaultBB;
	protected double downsampling = defaultDownsampling;
	protected int cacheTypeInputImg = defaultInputImgCacheType;
	protected int cacheTypeWeights = defaultWeightCacheType;
	protected int psfType = defaultPSFType;
	protected double osemSpeedup = defaultOsemSpeedup;
	protected int numIterations = defaultNumIterations;
	protected boolean debugMode = defaultDebugMode;
	protected int debugInterval = defaultDebugInterval;
	protected boolean useTikhonov = defaultUseTikhonovRegularization;
	protected double lambda = defaultLambda;
	protected int blockSizeIndex = defaultBlockSizeIndex;
	protected int[] blockSize = new int[]{ defaultBlockSizeX, defaultBlockSizeY, defaultBlockSizeZ };
	protected int cacheBlockSize = defaultCacheBlockSize;
	protected int cacheMaxNumBlocks = defaultCacheMaxNumBlocks;
	protected int psiCopyBlockSize = defaultPsiCopyBlockSize;
	protected int computeOnIndex = defaultComputeOnIndex;
	protected ImgFactory< FloatType > psiFactory = null;
	protected ImgFactory< FloatType > copyFactory = null;
	protected ImgFactory< FloatType > blockFactory = new ArrayImgFactory<>();
	protected ComputeBlockThreadFactory computeFactory = null;
	protected boolean adjustBlending = defaultAdjustBlending;
	protected float blendingRange = defaultBlendingRange;
	protected float blendingBorder = defaultBlendingBorder;
	protected boolean additionalSmoothBlending = defaultAdditionalSmoothBlending;
	protected boolean groupTiles = defaultGroupTiles;
	protected boolean groupIllums = defaultGroupIllums;
	protected int splittingType = defaultSplittingType;
	protected int imgExport = defaultImgExportAlgorithm;
	protected long[] maxBlock = null;
	
	final protected SpimData2 spimData;
	final List< ViewId > views;
	final List< BoundingBox > allBoxes;
	final ExecutorService service;
	final HashMap< ViewId, PointSpreadFunction > psfs;
	final long[] maxDimPSF;

	public DeconvolutionGUI( final SpimData2 spimData, final List< ViewId > views, final ExecutorService service )
	{
		this.spimData = spimData;
		this.views = new ArrayList<>();
		this.views.addAll( views );
		this.service = service;

		// filter not present ViewIds
		final List< ViewId > removed = SpimData2.filterMissingViews( spimData, views );
		if ( removed.size() > 0 ) IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

		// get all bounding boxes and two extra ones
		this.allBoxes = BoundingBoxTools.getAllBoundingBoxes( spimData, views, true );

		// check that all psfs are there, make a local lookup
		psfs = getPSFs();

		if ( psfs != null )
		{
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Computing maximal PSF size ... " );

			maxDimPSF = maxTransformedKernel( psfs, spimData.getViewRegistrations() );

			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Maximal transformed PSF size = " + Util.printCoordinates( maxDimPSF ) );
		}
		else
		{
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Error, not all views have PSF's assigned, please do that first." );

			maxDimPSF = null;
		}
	}

	@Override
	public SpimData2 getSpimData() { return spimData; }

	@Override
	public List< ViewId > getViews() { return views; }

	public BoundingBox getBoundingBox() { return allBoxes.get( boundingBox ); }

	@Override
	public Interval getDownsampledBoundingBox()
	{
		if ( !Double.isNaN( downsampling ) )
			return TransformVirtual.scaleBoundingBox( getBoundingBox(), 1.0 / downsampling );
		else
			return getBoundingBox();
	}

	@Override
	public int getPixelType() { return 0; }

	@Override
	public double getDownsampling(){ return downsampling; }

	@Override
	public int getSplittingType() { return splittingType; }

	public ImgDataType getInputImgCacheType() { return ImgDataType.values()[ cacheTypeInputImg ]; }
	public ImgDataType getWeightCacheType() { return ImgDataType.values()[ cacheTypeWeights ]; }
	public PSFTYPE getPSFType() { return PSFTYPE.values()[ psfType ]; }
	public double getOSEMSpeedUp() { return osemSpeedup; }
	public int getNumIterations() { return numIterations; }
	public boolean getDebugMode() { return debugMode; }
	public int getDebugInterval() { return debugInterval; }
	public boolean getUseTikhonov() { return useTikhonov; }
	public float getLambda() { return useTikhonov ? (float)lambda : 0.0f; }
	public int[] getComputeBlockSize() { return blockSize; }
	public int getCacheBlockSize() { return cacheBlockSize; }
	public int getCacheMaxNumBlocks(){ return cacheMaxNumBlocks; }
	public int getPsiCopyBlockSize() { return psiCopyBlockSize; }
	public ImgFactory< FloatType > getBlockFactory() { return blockFactory; }
	public ImgFactory< FloatType > getPsiFactory() { return psiFactory; }
	public ImgFactory< FloatType > getCopyFactory() { return copyFactory; }
	public ComputeBlockThreadFactory getComputeBlockThreadFactory() { return computeFactory; }
	public float getBlendingRange() { return blendingRange; }
	public float getBlendingBorder() { return blendingBorder; }
	public boolean getAdditionalSmoothBlending() { return additionalSmoothBlending; }
	public boolean groupTiles() { return groupTiles; }
	public boolean groupIllums() { return groupIllums; }
	public ImgExport getExporter() { return staticImgExportAlgorithms.get( imgExport ).newInstance(); }

	public boolean queryDetails()
	{
		if ( maxDimPSF == null )
			return false;

		final String[] choices = FusionGUI.getBoundingBoxChoices( allBoxes );

		if ( defaultBB >= choices.length )
			defaultBB = 0;

		Choice boundingBoxChoice, inputCacheChoice, weightCacheChoice, blockChoice, computeOnChoice, splitChoice;
		TextField downsampleField;

		final GenericDialog gd = new GenericDialog( "Image Fusion" );

		gd.addChoice( "Bounding_Box", choices, choices[ defaultBB ] );
		boundingBoxChoice = (Choice)gd.getChoices().lastElement();
		gd.addMessage( "" );

		gd.addSlider( "Downsampling", 1.0, 16.0, defaultDownsampling );
		downsampleField = (TextField)gd.getNumericFields().lastElement();
		gd.addChoice( "Input image(s)", FusionTools.imgDataTypeChoice, FusionTools.imgDataTypeChoice[ defaultInputImgCacheType ] );
		inputCacheChoice = (Choice)gd.getChoices().lastElement();
		gd.addChoice( "Weight image(s)", FusionTools.imgDataTypeChoice, FusionTools.imgDataTypeChoice[ defaultWeightCacheType ] );
		weightCacheChoice = (Choice)gd.getChoices().lastElement();

		gd.addMessage( "" );

		gd.addChoice( "Type_of_iteration", psfTypeString, psfTypeString[ defaultPSFType ] );
		gd.addNumericField( "OSEM_acceleration", defaultOsemSpeedup, 1 );
		gd.addNumericField( "Number_of_iterations", defaultNumIterations, 0 );
		gd.addCheckbox( "Debug_mode", defaultDebugMode );
		gd.addCheckbox( "Use_Tikhonov_regularization", defaultUseTikhonovRegularization );
		gd.addNumericField( "Tikhonov_parameter", defaultLambda, 4 );

		gd.addMessage( "" );

		gd.addChoice( "Compute", blocksChoice, blocksChoice[ defaultBlockSizeIndex ] );
		blockChoice = (Choice)gd.getChoices().lastElement();
		gd.addChoice( "Compute_on", computationOnChoice, computationOnChoice[ defaultComputeOnIndex ] );
		computeOnChoice = (Choice)gd.getChoices().lastElement();
		gd.addCheckbox( "Adjust_blending & grouping parameters", defaultAdjustBlending );

		gd.addMessage( "" );

		gd.addChoice( "Produce one fused image for", splittingTypes, splittingTypes[ defaultSplittingType ] );
		splitChoice = (Choice)gd.getChoices().lastElement();
		gd.addChoice( "Fused_image", imgExportDescriptions, imgExportDescriptions[ defaultImgExportAlgorithm ] );

		gd.addMessage( "Estimated size: ", GUIHelper.largestatusfont, GUIHelper.good );
		Label label1 = (Label)gd.getMessage();
		gd.addMessage( "???x???x??? pixels", GUIHelper.smallStatusFont, GUIHelper.good );
		Label label2 = (Label)gd.getMessage();

		final ManageDeconvolutionDialogListeners m = new ManageDeconvolutionDialogListeners(
				gd,
				boundingBoxChoice,
				downsampleField,
				inputCacheChoice,
				weightCacheChoice,
				blockChoice,
				computeOnChoice,
				splitChoice,
				label1,
				label2,
				this );

		m.update();

		gd.showDialog();

		if ( gd.wasCanceled() )
			return false;

		boundingBox = defaultBB = gd.getNextChoiceIndex();
		downsampling = defaultDownsampling = gd.getNextNumber();

		if ( downsampling == 1.0 )
			downsampling = Double.NaN;

		cacheTypeInputImg = defaultInputImgCacheType = gd.getNextChoiceIndex();
		cacheTypeWeights = defaultWeightCacheType = gd.getNextChoiceIndex();
		psfType = defaultPSFType = gd.getNextChoiceIndex();
		osemSpeedup = defaultOsemSpeedup = gd.getNextNumber();
		numIterations = defaultNumIterations = (int)Math.round( gd.getNextNumber() );
		debugMode = defaultDebugMode = gd.getNextBoolean();
		useTikhonov = defaultUseTikhonovRegularization = gd.getNextBoolean();
		lambda = defaultLambda = gd.getNextNumber();
		blockSizeIndex = defaultBlockSizeIndex = gd.getNextChoiceIndex();
		computeOnIndex = defaultComputeOnIndex = gd.getNextChoiceIndex();
		adjustBlending = defaultAdjustBlending = gd.getNextBoolean();
		splittingType = defaultSplittingType = gd.getNextChoiceIndex();
		imgExport = defaultImgExportAlgorithm = gd.getNextChoiceIndex();

		if ( !getDebug() )
			return false;

		if ( !getBlocks() )
			return false;

		psiFactory = new CellImgFactory<>( psiCopyBlockSize );
		copyFactory = new CellImgFactory<>( psiCopyBlockSize );

		if ( !getBlendingAndGrouping() )
			return false;

		if ( !getComputeDevice() )
			return false;

		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Selected (MultiView)Deconvolution Parameters: " );
		IOFunctions.println( "Bounding Box: " + getBoundingBox() );
		IOFunctions.println( "Downsampling: " + DownsampleTools.printDownsampling( getDownsampling() ) );
		IOFunctions.println( "Downsampled Bounding Box: " + getDownsampledBoundingBox() );
		IOFunctions.println( "Input Image Cache Type: " + FusionTools.imgDataTypeChoice[ getInputImgCacheType().ordinal() ] );
		IOFunctions.println( "Weight Cache Type: " + FusionTools.imgDataTypeChoice[ getWeightCacheType().ordinal() ] );
		IOFunctions.println( "PSF Type: " + psfTypeString[ getPSFType().ordinal() ] );
		IOFunctions.println( "OSEMSpeedup: " + osemSpeedup );
		IOFunctions.println( "Num Iterations: " + numIterations );
		IOFunctions.println( "Debug Mode: " + debugMode );
		if ( debugMode ) IOFunctions.println( "DebugInterval: " + debugInterval );
		IOFunctions.println( "use Tikhonov: " + useTikhonov );
		if ( useTikhonov ) IOFunctions.println( "Tikhonov Lambda: " + lambda );
		IOFunctions.println( "Compute block size: " + Util.printCoordinates( blockSize ) );
		IOFunctions.println( "Cache block size: " + cacheBlockSize );
		IOFunctions.println( "Cache max num blocks: " + cacheMaxNumBlocks );
		IOFunctions.println( "Deconvolved/Copy block size: " + psiCopyBlockSize );
		IOFunctions.println( "Compute on: " + computationOnChoice[ computeOnIndex ] );
		IOFunctions.println( "ComputeBlockThread Factory: " + computeFactory.getClass().getSimpleName() + ": " + computeFactory );
		IOFunctions.println( "Blending range: " + blendingRange );
		IOFunctions.println( "Blending border: " + blendingBorder );
		IOFunctions.println( "Additional smooth blending: " + additionalSmoothBlending );
		IOFunctions.println( "Group tiles: " + groupTiles );
		IOFunctions.println( "Group illums: " + groupIllums );
		IOFunctions.println( "Split by: " + splittingTypes[ getSplittingType() ] );
		IOFunctions.println( "Image Export: " + imgExportDescriptions[ imgExport ] );
		IOFunctions.println( "ImgLoader.isVirtual(): " + isImgLoaderVirtual() );
		IOFunctions.println( "ImgLoader.isMultiResolution(): " + isMultiResolution() );

		return true;
	}

	public boolean isImgLoaderVirtual() { return FusionGUI.isImgLoaderVirtual( spimData ); }
	public boolean isMultiResolution() { return FusionGUI.isMultiResolution( spimData ); }

	public List< Group< ViewDescription > > getFusionGroups()
	{
		return FusionGUI.getFusionGroups( getSpimData(), getViews(), getSplittingType() );
	}

	public List< Group< ViewDescription > > getDeconvolutionGrouping( final Group< ViewDescription > group )
	{
		final HashSet< Class< ? extends Entity > > groupingFactors = new HashSet<>();

		if ( groupTiles )
			groupingFactors.add( Tile.class );

		if ( groupIllums )
			groupingFactors.add( Illumination.class );

		final ArrayList< ViewDescription > list = new ArrayList<>();
		list.addAll( group.getViews() );

		return Group.combineBy( list, groupingFactors );
	}

	public HashMap< ViewId, PointSpreadFunction > getPSFs()
	{
		final HashMap< ViewId, PointSpreadFunction > psfs = new HashMap<>();

		for ( final ViewId view : views )
		{
			if ( !spimData.getPointSpreadFunctions().getPointSpreadFunctions().containsKey( view ) )
			{
				IOFunctions.println( new Date( System.currentTimeMillis() ) + ": ERROR - No PSF assigned for view " + Group.pvid( view ) );
				return null;
			}
			else
			{
				psfs.put( view, spimData.getPointSpreadFunctions().getPointSpreadFunctions().get( view ) );
			}
		}

		return psfs;
	}

	protected boolean getDebug()
	{
		if ( debugMode )
		{
			GenericDialog gdDebug = new GenericDialog( "Debug options" );
			gdDebug.addNumericField( "Show debug output every n'th frame, n = ", defaultDebugInterval, 0 );
			gdDebug.showDialog();

			if ( gdDebug.wasCanceled() )
				return false;

			defaultDebugInterval = debugInterval = (int)Math.round( gdDebug.getNextNumber() );
		}

		return true;
	}

	protected boolean getBlocks()
	{
		if ( blockSizeIndex == 0 )
		{
			this.blockSize = new int[]{ 256, 256, 256 };
		}
		else if ( blockSizeIndex == 1 )
		{
			this.blockSize = new int[]{ 512, 512, 512 };
		}
		else if ( blockSizeIndex == 2 )
		{
			this.blockSize = new int[]{ 768, 768, 768 };
		}
		else if ( blockSizeIndex == 3 )
		{
			this.blockSize = new int[]{ 1024, 1024, 1024 };
		}
		else if ( blockSizeIndex == 4 )
		{
			GenericDialog gd = new GenericDialog( "Define block sizes" );

			gd.addNumericField( "Compute_blocksize_x", defaultBlockSizeX, 0 );
			gd.addNumericField( "Compute_blocksize_y", defaultBlockSizeY, 0 );
			gd.addNumericField( "Compute_blocksize_z", defaultBlockSizeZ, 0 );

			gd.addMessage( "Note: block sizes shouldn't be smaller than 128 pixels", GUIHelper.smallStatusFont );
			gd.addMessage( "" );

			gd.addNumericField( "Deconvolved_image_block_size", defaultPsiCopyBlockSize, 0 );
			gd.addMessage( "Note: this values defines the block size for the deconvolved & copied images", GUIHelper.smallStatusFont );
			gd.addMessage( "" );

			if ( cacheTypeInputImg == 1 || cacheTypeWeights == 1 )
			{
				gd.addNumericField( "Cache_block_size", defaultCacheBlockSize, 0 );
				gd.addNumericField( "Cache_max num blocks", defaultCacheMaxNumBlocks, 0 );
				gd.addMessage( "Note: these values define the cache parameters for input images & weights", GUIHelper.smallStatusFont );
			}

			gd.showDialog();

			if ( gd.wasCanceled() )
				return false;

			this.blockSize = new int[]{
					defaultBlockSizeX = Math.max( 1, (int)Math.round( gd.getNextNumber() ) ),
					defaultBlockSizeY = Math.max( 1, (int)Math.round( gd.getNextNumber() ) ),
					defaultBlockSizeZ = Math.max( 1, (int)Math.round( gd.getNextNumber() ) ) };

			this.psiCopyBlockSize = defaultPsiCopyBlockSize = Math.max( 1, (int)Math.round( gd.getNextNumber() ) );

			if ( cacheTypeInputImg == 1 || cacheTypeWeights == 1 )
			{
				this.cacheBlockSize = defaultCacheBlockSize = Math.max( 1, (int)Math.round( gd.getNextNumber() ) );
				this.cacheMaxNumBlocks = defaultCacheMaxNumBlocks = Math.max( 1, (int)Math.round( gd.getNextNumber() ) );
			}
		}
		else
		{
			this.blockSize = new int[]{ (int)maxBlock[ 0 ], (int)maxBlock[ 1 ], (int)maxBlock[ 2 ] };
		}

		return true;
	}

	protected boolean getComputeDevice()
	{
		if ( computeOnIndex == 0 )
		{
			this.computeFactory = new ComputeBlockThreadCPUFactory( service, MultiViewDeconvolution.minValue, getLambda(), blockSize, blockFactory );
		}
		else if ( computeOnIndex == 1 )
		{
			final ArrayList< String > potentialNames = new ArrayList< String >();
			potentialNames.add( "fftCUDA" );
			potentialNames.add( "FourierConvolutionCUDA" );

			final CUDAFourierConvolution cuda = NativeLibraryTools.loadNativeLibrary( potentialNames, CUDAFourierConvolution.class );

			if ( cuda == null )
			{
				IOFunctions.println( "Cannot load CUDA JNA library." );
				return false;
			}

			final ArrayList< CUDADevice > selectedDevices = CUDATools.queryCUDADetails( cuda, true );

			if ( selectedDevices == null || selectedDevices.size() == 0 )
				return false;

			final HashMap< Integer, CUDADevice > idToCudaDevice = new HashMap<>();

			for ( int devId = 0; devId < selectedDevices.size(); ++devId )
				idToCudaDevice.put( devId, selectedDevices.get( devId ) );

			this.computeFactory = new ComputeBlockThreadCUDAFactory( service, MultiViewDeconvolution.minValue, getLambda(), blockSize, cuda, idToCudaDevice );
		}
		else
		{
			throw new RuntimeException( "Unknown compute device index: " + computeOnIndex );
		}

		return true;
	}

	protected boolean getBlendingAndGrouping()
	{
		if ( adjustBlending )
		{
			final GenericDialog gd = new GenericDialog( "Adjust blending & grouping parameters" );

			gd.addSlider( "Blending_boundary (pixels)", -50, 50, defaultBlendingBorder );
			gd.addSlider( "Blending_range (pixels)", 0, 100, defaultBlendingRange );
			gd.addCheckbox( "Additional_smooth_blending", defaultAdditionalSmoothBlending );

			gd.addMessage( "" );

			gd.addMessage( "Note: both sizes are in local coordinates of the input views. Increase one or both of those values if stripy artifacts\n" +
						   "are visible in the deconvolution result, or choose additional smooth blending as an option\n" +
						   "The boundary pixels describe a range of pixels at the edge of each input view that are discarded because of the PSF size,\n" +
						   "it should typically correspond to half the size of the extracted PSF.\n" +
						   "A negative boundary value means that the deconvolution will make an educated guess even on pixels that were not\n" +
						   "directly imaged,based on the fact that parts of their signal is visible due to the size of the PSF", GUIHelper.smallStatusFont );

			gd.addMessage( "" );

			if ( splittingType <= 2 )
				gd.addCheckbox( "Group_tiles into one view", defaultGroupTiles );
			if ( splittingType == 0 || splittingType == 2 )
				gd.addCheckbox( "Group_illuminations into one view", defaultGroupIllums );

			gd.showDialog();

			if ( gd.wasCanceled() )
				return false;

			blendingBorder = defaultBlendingBorder = (float)gd.getNextNumber();
			blendingRange = defaultBlendingRange = (float)gd.getNextNumber();
			additionalSmoothBlending = defaultAdditionalSmoothBlending = gd.getNextBoolean();

			if ( splittingType <= 2 )
				groupTiles = defaultGroupTiles = gd.getNextBoolean();
			else
				groupTiles = false;

			if ( splittingType == 0 || splittingType == 2 )
				groupIllums = defaultGroupIllums = gd.getNextBoolean();
			else
				groupIllums = false;
		}

		return true;
	}

	public static long[] maxTransformedKernel( final HashMap< ViewId, PointSpreadFunction > psfs, final ViewRegistrations vr )
	{
		long[] maxDim = null;
		int n = -1;

		for ( final ViewId viewId : psfs.keySet() )
		{
			final PointSpreadFunction psf = psfs.get( viewId );
			final Img< FloatType > img = psf.getPSFCopy();

			if ( maxDim == null )
			{
				n = img.numDimensions();
				maxDim = new long[ n ];
			}

			final ViewRegistration v = vr.getViewRegistration( viewId );
			v.updateModel();
			final FinalRealInterval bounds = v.getModel().estimateBounds( img );

			System.out.println( Group.pvid( viewId ) + ": " + IOFunctions.printRealInterval( bounds ) );

			// +3 should be +1, but just to be safe
			for ( int d = 0; d < maxDim.length; ++d )
				maxDim[ d ] = Math.max( maxDim[ d ], Math.round( Math.abs( bounds.realMax( d ) - bounds.realMin( d ) ) ) + 3 );
		}

		return maxDim;
	}
}
