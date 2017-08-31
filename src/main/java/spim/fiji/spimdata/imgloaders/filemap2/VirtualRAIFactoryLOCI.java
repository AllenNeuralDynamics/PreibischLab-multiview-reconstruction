package spim.fiji.spimdata.imgloaders.filemap2;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import org.scijava.Context;
import org.scijava.options.OptionsService;

import bdv.util.BdvFunctions;
import loci.formats.FormatException;
import loci.formats.FormatTools;
import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import net.imglib2.Dimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import spim.fiji.spimdata.imgloaders.LegacyStackImgLoaderLOCI;
import spim.process.fusion.FusionTools;

public class VirtualRAIFactoryLOCI
{
	@FunctionalInterface
	interface TriConsumer<A,B,C>
	{
		void accept(A a, B b, C c);
		
		default TriConsumer< A, B, C > andThen(TriConsumer< ? super A, ? super B, ? super C > after)
		{
			Objects.requireNonNull( after );

			return (a, b, c) -> {
				accept( a, b, c );
				after.accept( a, b, c );
			};
		}
	}
	
	@SuppressWarnings("unchecked")
	public <T extends RealType< T > & NativeType< T >> RandomAccessibleInterval< T > createVirtual(
			final IFormatReader reader,
			final File file,
			final int series,
			final int channel,
			final int timepoint,
			T type,
			Dimensions dim) throws IncompatibleTypeException
	{
		setReaderFileAndSeriesIfNecessary( reader, file, series );

		final boolean isLittleEndian = reader.isLittleEndian();		
		final long[] dims = new long[]{reader.getSizeX(), reader.getSizeY(), reader.getSizeZ()};

		if (dim != null)
			dim.dimensions( dims );

		final int pixelType = reader.getPixelType();
		if (pixelType == FormatTools.UINT8)
			return new VirtualRandomAccessibleIntervalLOCI< T >( reader, file, dims, series, channel, timepoint, type == null ? (T) new UnsignedByteType() : type, (t, buf, i) -> {t.setReal( (int) buf[i] & 0xff);} );
		else if (pixelType == FormatTools.UINT16)
			return new VirtualRandomAccessibleIntervalLOCI< T >( reader, file, dims, series, channel, timepoint, type == null ? (T) new UnsignedShortType() : type, (t, buf, i) -> {t.setReal( LegacyStackImgLoaderLOCI.getShortValueInt( buf, i*2, isLittleEndian ) );} );
		else if (pixelType == FormatTools.INT16)
			return new VirtualRandomAccessibleIntervalLOCI< T >( reader, file, dims, series, channel, timepoint, type == null ? (T) new ShortType() : type, (t, buf, i) -> {t.setReal( LegacyStackImgLoaderLOCI.getShortValue( buf, i*2, isLittleEndian ) );} );
		else if (pixelType == FormatTools.UINT32)
			return new VirtualRandomAccessibleIntervalLOCI< T >( reader, file, dims, series, channel, timepoint, type == null ? (T) new UnsignedIntType() : type, (t, buf, i) -> {t.setReal( LegacyStackImgLoaderLOCI.getIntValue( buf, i*4, isLittleEndian ) );} );
		else if (pixelType == FormatTools.FLOAT)
			return new VirtualRandomAccessibleIntervalLOCI< T >( reader, file, dims, series, channel, timepoint, type == null ? (T) new FloatType() : type, (t, buf, i) -> {t.setReal( LegacyStackImgLoaderLOCI.getFloatValue( buf, i*4, isLittleEndian ) );} );
		else
			throw new IncompatibleTypeException( this, "cannot create virtual image for this pixel type" );
	}
	
	@SuppressWarnings("unchecked")
	public synchronized <T extends RealType< T > & NativeType< T >> RandomAccessibleInterval< T > createVirtualCached(
			final IFormatReader reader,
			final File file,
			final int series,
			final int channel,
			final int timepoint,
			T type,
			Dimensions dim) throws IncompatibleTypeException
	{
		setReaderFileAndSeriesIfNecessary( reader, file, series );

		final boolean isLittleEndian = reader.isLittleEndian();		
		final long[] dims = new long[]{reader.getSizeX(), reader.getSizeY(), reader.getSizeZ()};

		if (dim != null)
			dim.dimensions( dims );

		final int pixelType = reader.getPixelType();
		
		if (pixelType == FormatTools.UINT8)
		{
			RandomAccessibleInterval< T > virtualImg = new VirtualRandomAccessibleIntervalLOCI< T >( reader, file, dims, series, channel, timepoint, type == null ? (T) new UnsignedByteType() : type, (t, buf, i) -> {t.setReal( (int) buf[i] & 0xff);} );
			return FusionTools.cacheRandomAccessibleInterval( virtualImg, Integer.MAX_VALUE, type == null ? (T) new UnsignedByteType() : type, new int[] {(int)virtualImg.dimension( 0 ), (int)virtualImg.dimension( 1 ), 1} ) ;
		}
		else if (pixelType == FormatTools.UINT16)
		{
			RandomAccessibleInterval< T > virtualImg = new VirtualRandomAccessibleIntervalLOCI< T >( reader, file, dims, series, channel, timepoint, type == null ? (T) new UnsignedShortType() : type, (t, buf, i) -> {t.setReal( LegacyStackImgLoaderLOCI.getShortValueInt( buf, i*2, isLittleEndian ) );} );
			return FusionTools.cacheRandomAccessibleInterval( virtualImg, Integer.MAX_VALUE, type == null ? (T) new UnsignedShortType() : type, new int[] {(int)virtualImg.dimension( 0 ), (int)virtualImg.dimension( 1 ), 1} ) ;
		}
		else if (pixelType == FormatTools.INT16)
		{
			RandomAccessibleInterval< T > virtualImg = new VirtualRandomAccessibleIntervalLOCI< T >( reader, file, dims, series, channel, timepoint, type == null ? (T) new ShortType() : type, (t, buf, i) -> {t.setReal( LegacyStackImgLoaderLOCI.getShortValue( buf, i*2, isLittleEndian ) );} );
			return FusionTools.cacheRandomAccessibleInterval( virtualImg, Integer.MAX_VALUE, type == null ? (T) new ShortType() : type, new int[] {(int)virtualImg.dimension( 0 ), (int)virtualImg.dimension( 1 ), 1} ) ;
		}
		else if (pixelType == FormatTools.UINT32)
		{
			RandomAccessibleInterval< T > virtualImg = new VirtualRandomAccessibleIntervalLOCI< T >( reader, file, dims, series, channel, timepoint, type == null ? (T) new UnsignedIntType() : type, (t, buf, i) -> {t.setReal( LegacyStackImgLoaderLOCI.getIntValue( buf, i*4, isLittleEndian ) );} );
			return FusionTools.cacheRandomAccessibleInterval( virtualImg, Integer.MAX_VALUE, type == null ? (T) new UnsignedIntType() : type, new int[] {(int)virtualImg.dimension( 0 ), (int)virtualImg.dimension( 1 ), 1} ) ;
		}
		else if (pixelType == FormatTools.FLOAT)
		{
			RandomAccessibleInterval< T > virtualImg = new VirtualRandomAccessibleIntervalLOCI< T >( reader, file, dims, series, channel, timepoint, type == null ? (T) new FloatType() : type, (t, buf, i) -> {t.setReal( LegacyStackImgLoaderLOCI.getFloatValue( buf, i*4, isLittleEndian ) );} );
			return FusionTools.cacheRandomAccessibleInterval( virtualImg, Integer.MAX_VALUE,  type == null ? (T) new FloatType() : type, new int[] {(int)virtualImg.dimension( 0 ), (int)virtualImg.dimension( 1 ), 1} ) ;
		}
		else
			throw new IncompatibleTypeException( this, "cannot create virtual image for this pixel type: " + pixelType );
	}
	
	/**
	 * ensure that the reader we have is set to the correct file and series
	 */
	public static void setReaderFileAndSeriesIfNecessary(final IFormatReader reader, final File file, final int series)
	{
		if (reader.getCurrentFile() == null || !reader.getCurrentFile().equals( file.getAbsolutePath() ))
		{
			try
			{
				reader.setId( file.getAbsolutePath() );
			}
			catch ( FormatException | IOException e )
			{
				e.printStackTrace();
				System.exit( 1 );
			}
			reader.setSeries( series );
		}
		else
		{
			if (reader.getSeries() != series)
				reader.setSeries( series );
		}
	}

	/** check if the given reader is set to the given file and series
	 * 
	 * @param reader the reader
	 * @param file the file
	 * @param series the series
	 * @return true or false
	 */
	public static boolean checkReaderFileAndSeries(final IFormatReader reader, final File file, final int series)
	{
		if (reader.getCurrentFile() == null || !reader.getCurrentFile().equals( file.getAbsolutePath() ))
			return false;
		else
			return reader.getSeries() == series;
	}
	
	public  static <T extends RealType<T> & NativeType< T > > void main(String[] args)
	{
		RandomAccessibleInterval< T > img = null;
		ImageReader reader = new ImageReader();
		try
		{
		img = new VirtualRAIFactoryLOCI().createVirtualCached( reader, new File( "/Users/david/Desktop/2ch2ill2angle.czi" ), 0, 2, 0 , (T) new DoubleType(), null);
		}
		catch ( IncompatibleTypeException e )
		{
			e.printStackTrace();
		}
		
		Img< T > create = new CellImgFactory<T>().create( img, Views.iterable( img ).firstElement().createVariable() );
		
		
		
		System.out.println( Views.iterable( img ).firstElement().getClass());
		ImageJFunctions.show( img, "BDV" );
		System.out.println( reader.getModuloC().step );
	}
}
