package spim.fiji.spimdata.imgloaders.filemap2;

import java.io.File;
import java.io.IOException;

import loci.formats.FormatException;
import loci.formats.IFormatReader;
import net.imglib2.AbstractInterval;
import net.imglib2.Interval;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Sampler;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import spim.fiji.spimdata.imgloaders.filemap2.VirtualRAIFactoryLOCI.TriConsumer;

class VirtualRandomAccessibleIntervalLOCI<T extends RealType< T > & NativeType< T >> extends AbstractInterval
		implements RandomAccessibleInterval< T >
{
	private final IFormatReader reader;
	private final File file;
	private final int series;
	private final int channel;
	private final int timepoint;
	private final T type;
	private final TriConsumer< T, byte[], Integer > byteConverter;

	VirtualRandomAccessibleIntervalLOCI(IFormatReader reader, File file, long[] dims, int series, int channel,
			int timepoint, T type, final TriConsumer< T, byte[], Integer > byteConverter)
	{
		super( dims );
		this.reader = reader;
		this.file = file;
		this.series = series;
		this.channel = channel;
		this.timepoint = timepoint;
		this.type = type;
		this.byteConverter = byteConverter;
	}

	@Override
	public RandomAccess< T > randomAccess()
	{
		return new VirtualRandomAccessLOCI();
	}

	@Override
	public RandomAccess< T > randomAccess(Interval interval)
	{
		return randomAccess();
	}

	private class VirtualRandomAccessLOCI extends Point implements RandomAccess< T >
	{

		private byte[] buffer;
		private T type;
		private int currentZ = -1;

		private VirtualRandomAccessLOCI()
		{
			super( 3 );
			this.type = VirtualRandomAccessibleIntervalLOCI.this.type.createVariable();
			buffer = new byte[0];

		}

		private void readIntoBuffer()
		{

			VirtualRAIFactoryLOCI.setReaderFileAndSeriesIfNecessary( reader, file, series );

			int siz = reader.getBitsPerPixel() / 8 * reader.getRGBChannelCount() * reader.getSizeX()
					* reader.getSizeY();
			buffer = new byte[siz];

//			System.out.println( "reading z plane " + position[2] + " from series " + series + " in file " + file.getAbsolutePath() );

			try
			{
				// the image is RGB -> we have to read bytes for all channels at once?
				if (reader.getRGBChannelCount() == reader.getSizeC())
					reader.openBytes( reader.getIndex( (int) position[2], 0, timepoint), buffer );
				// normal image -> read specified channel
				else
					reader.openBytes( reader.getIndex( (int) position[2], channel, timepoint), buffer );
			}
			catch ( FormatException | IOException e )
			{
				e.printStackTrace();
			}
		}

		@Override
		public T get()
		{
			// prevent multithreaded overwriting of buffer
			synchronized ( reader )
			{
				if ( position[2] != currentZ  || !VirtualRAIFactoryLOCI.checkReaderFileAndSeries( reader, file, series ))
				{
					currentZ = (int) position[2];
					readIntoBuffer();
				}

				int rgbOffset = 0;
				if (reader.getRGBChannelCount() == reader.getSizeC())
					rgbOffset = channel * buffer.length / reader.getSizeC();
					
				// pixel index (we do not care about bytesPerPixel here, byteCOnverter should take care of that)
				final int i = (int) (rgbOffset + position[0] + position[1] * VirtualRandomAccessibleIntervalLOCI.this.dimension( 0 ) );
				byteConverter.accept( type, buffer, i );
				return this.type;
			}
		}

		@Override
		public Sampler< T > copy()
		{
			return copyRandomAccess();
		}

		@Override
		public RandomAccess< T > copyRandomAccess()
		{
			return new VirtualRandomAccessLOCI();
		}

	}

}
