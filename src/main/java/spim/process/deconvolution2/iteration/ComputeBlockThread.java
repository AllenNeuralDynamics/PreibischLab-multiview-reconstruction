package spim.process.deconvolution2.iteration;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.type.numeric.real.FloatType;
import spim.process.cuda.Block;
import spim.process.deconvolution2.DeconView;

/**
 * Executes one Lucy-Richardson iteration on one specifc block.
 * When initialized, it is not relevant which block it will be,
 * since it only depends on the block size
 *
 * @author stephan.preibisch@gmx.de
 *
 */
public interface ComputeBlockThread
{
	/**
	 * @return the block size in which we process
	 */
	public int[] getBlockSize();

	/**
	 * @return the minimum value inside the deconvolved image
	 */
	public float getMinValue();

	/**
	 * @return the unique id of this thread, greater or equal to 0, starting at 0 and increasing by 1 each thread
	 */
	public int getId();

	/**
	 * contains the deconvolved image at the current iteration, copied into a block from outside (outofbounds is mirror)
	 *
	 * @return the Img to use in order to provide the copied psiBlock
	 */
	public Img< FloatType > getPsiBlockTmp();

	/**
	 * run an iteration on the block
	 *
	 * @param view - the input view
	 * @param block - the Block instance
	 * @param imgBlock - the input image as block (virtual, outofbounds is zero)
	 * @param weightBlock - the weights for this image (virtual, outofbounds is zero)
	 * @param maxIntensityView - the maximum intensity of the view
	 * @param kernel1 - psf1
	 * @param kernel2 - psf2
	 * @return - statistics of this block
	 */
	public IterationStatistics runIteration(
			final DeconView view,
			final Block block,
			final RandomAccessibleInterval< FloatType > imgBlock, // out of bounds is ZERO
			final RandomAccessibleInterval< FloatType > weightBlock,
			final float maxIntensityView,
			final ArrayImg< FloatType, ? > kernel1,
			final ArrayImg< FloatType, ? > kernel2 );
	//
	// convolve psi (current guess of the image) with the PSF of the current view
	// [psi >> tmp1]
	//

	//
	// compute quotient img/psiBlurred
	// [tmp1, img >> tmp1]
	//

	//
	// blur the residuals image with the kernel
	// (this cannot be don in-place as it might be computed in blocks sequentially,
	// and the input for the n+1'th block cannot be formed by the written back output
	// of the n'th block)
	// [tmp1 >> tmp2]
	//

	//
	// compute final values
	// [psi, weights, tmp2 >> psi]
	//

	public class IterationStatistics
	{
		public double sumChange = 0;
		public double maxChange = -1;
	}
}
