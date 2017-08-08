package spim.process.deconvolution;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import bdv.util.ConstantRandomAccessible;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
import spim.Threads;
import spim.process.cuda.Block;
import spim.process.cuda.BlockGeneratorFixedSizePrecise;
import spim.process.cuda.BlockSorter;
import spim.process.deconvolution.DeconViewPSF.PSFTYPE;
import spim.process.fusion.FusionTools;
import spim.process.fusion.ImagePortion;

/**
 * One view for the multiview deconvolution, contains image, weight, and PSFs
 *
 * @author stephan.preibisch@gmx.de
 *
 */
public class DeconView
{
	public static int[] defaultBlockSize = new int[]{ 384, 384, 384 };

	final DeconViewPSF psf;
	final RandomAccessibleInterval< FloatType > image, weight;

	final int n, numBlocks;
	final int[] blockSize;
	final List< List< Block > > nonInterferingBlocks;

	public DeconView(
			final ExecutorService service,
			final RandomAccessibleInterval< FloatType > image,
			final ArrayImg< FloatType, ? > kernel )
	{
		this(
				service,
				image,
				Views.interval(
						new ConstantRandomAccessible< FloatType >(
								new FloatType( 1 ),
								image.numDimensions() ),
						new FinalInterval( image ) ),
				kernel );
	}

	public DeconView(
			final ExecutorService service,
			final RandomAccessibleInterval< FloatType > image,
			final RandomAccessibleInterval< FloatType > weight,
			final ArrayImg< FloatType, ? > kernel )
	{
		this( service, image, weight, kernel, PSFTYPE.INDEPENDENT, defaultBlockSize, 1, true );
	}

	public DeconView(
			final ExecutorService service,
			final RandomAccessibleInterval< FloatType > image,
			final RandomAccessibleInterval< FloatType > weight,
			final ArrayImg< FloatType, ? > kernel,
			final int[] blockSize )
	{
		this( service, image, weight, kernel, PSFTYPE.INDEPENDENT, blockSize, 1, true );
	}

	public DeconView(
			final ExecutorService service,
			final RandomAccessibleInterval< FloatType > image,
			final RandomAccessibleInterval< FloatType > weight,
			final ArrayImg< FloatType, ? > kernel,
			final PSFTYPE psfType,
			final int[] blockSize,
			final boolean filterBlocksForContent )
	{
		this( service, image, weight, kernel, psfType, blockSize, 1, filterBlocksForContent );
	}

	public DeconView(
			final ExecutorService service,
			final RandomAccessibleInterval< FloatType > image,
			final RandomAccessibleInterval< FloatType > weight,
			final ArrayImg< FloatType, ? > kernel,
			final PSFTYPE psfType,
			final int[] blockSize,
			final int minRequiredBlocks,
			final boolean filterBlocksForContent )
	{
		this.n = image.numDimensions();
		this.psf = new DeconViewPSF( kernel, psfType );

		if ( Views.isZeroMin( image ) )
			this.image = image;
		else
			this.image = Views.zeroMin( image );

		if ( Views.isZeroMin( weight ) )
			this.weight = weight;
		else
			this.weight = Views.zeroMin( weight );

		this.blockSize = new int[ n ];

		// define the blocksize so that it is one single block
		for ( int d = 0; d < this.blockSize.length; ++d )
			this.blockSize[ d ] = blockSize[ d ];

		final long[] imgSize = new long[ n ];
		final long[] kernelSize = new long[ n ];

		image.dimensions( imgSize );
		kernel.dimensions( kernelSize );

		final BlockGeneratorFixedSizePrecise blockGenerator = new BlockGeneratorFixedSizePrecise( service, Util.int2long( this.blockSize ) );

		// we need double the kernel size since we convolve twice in one run
		for ( int d = 0; d < n; ++d )
			kernelSize[ d ] = kernelSize[ d ] * 2 - 1;

		final ArrayList< Block > blocks = blockGenerator.divideIntoBlocks( imgSize, kernelSize );

		if ( blocks == null )
		{
			this.numBlocks = -1;
			this.nonInterferingBlocks = null;

			return;
		}
		else
		{
			this.numBlocks = blocks.size();

			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Number of blocks: " + numBlocks + ", dim=" + Util.printCoordinates( this.blockSize ) + ", Effective size of each block (due to kernel size) " + Util.printCoordinates( blocks.get( 0 ).getEffectiveSize() ) );

			this.nonInterferingBlocks = BlockSorter.sortBlocksBySmallestFootprint( blocks, new FinalInterval( image ), minRequiredBlocks );

			if ( filterBlocksForContent )
			{
				final Pair< Integer, Integer > removed = filterBlocksForContent( nonInterferingBlocks, weight, service );

				if ( removed.getA() > 0 )
					IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Removed " + removed.getA() + " blocks, " + removed.getB() + " entire batches" );
			}
		}
	}

	public RandomAccessibleInterval< FloatType > getImage() { return image; }
	public RandomAccessibleInterval< FloatType > getWeight() { return weight; }
	public DeconViewPSF getPSF() { return psf; }
	public int[] getBlockSize() { return blockSize; }
	public List< List< Block > > getNonInterferingBlocks() { return nonInterferingBlocks; }
	public int getNumBlocks() { return numBlocks; }

	public static Pair< Integer, Integer > filterBlocksForContent( final List< List< Block > > blocksList, final RandomAccessibleInterval< FloatType > weight, final ExecutorService service )
	{
		int removeBlocks = 0;
		int removeBlockBatch = 0;

		for ( int j = blocksList.size() - 1; j >= 0; --j )
		{
			final List< Block > blocks = blocksList.get( j );

			for ( int i = blocks.size() - 1; i >= 0; --i )
			{
				if ( !blockContainsContent( blocks.get( i ), weight, service ) )
				{
					blocks.remove( i );
					++removeBlocks;
				}
			}

			if ( blocks.size() == 0 )
			{
				blocksList.remove( j );
				++removeBlockBatch;
			}
		}

		return new ValuePair<>( removeBlocks, removeBlockBatch );
	}

	//public static boolean debug = false;

	public static boolean blockContainsContent( final Block blockStruct, final RandomAccessibleInterval< FloatType > weight, final ExecutorService service )
	{
		final IterableInterval< FloatType > toTest = Views.iterable( Views.interval( Views.extendZero( weight ), blockStruct ) );

		final int nPortions = Threads.numThreads() * 4;
		final Vector< ImagePortion > portions = FusionTools.divideIntoPortions( toTest.size(), nPortions );
		final ArrayList< Callable< Boolean > > tasks = new ArrayList<>();

		for ( final ImagePortion portion : portions )
		{
			tasks.add( new Callable< Boolean >()
			{
				@Override
				public Boolean call() throws Exception
				{
					final Cursor< FloatType > c = toTest.cursor();

					c.jumpFwd( portion.getStartPosition() );

					for ( long l = 0; l < portion.getLoopSize(); ++l )
						if ( c.next().get() != 0.0 )
							return true;

					return false;
				}
			});
		}

		try
		{
			// invokeAll() returns when all tasks are complete
			for ( final Future< Boolean > results : service.invokeAll( tasks ) )
				if ( results.get() == true )
					return true;

			return false;
		}
		catch ( final Exception e )
		{
			IOFunctions.println( "Failed to identify if block contains data: " + e );
			e.printStackTrace();
			return true;
		}
	}
}
