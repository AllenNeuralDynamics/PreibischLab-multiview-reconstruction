package spim.fiji.plugin.interestpointregistration.pairwise;

import ij.gui.GenericDialog;
import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.plugin.interestpointregistration.TransformationModelGUI;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.process.interestpointregistration.pairwise.MatcherPairwise;
import spim.process.interestpointregistration.pairwise.constellation.grouping.GroupedInterestPoint;
import spim.process.interestpointregistration.pairwise.methods.icp.IterativeClosestPointPairwise;
import spim.process.interestpointregistration.pairwise.methods.icp.IterativeClosestPointParameters;
import spim.process.interestpointregistration.pairwise.methods.ransac.RANSACParameters;

/**
 * Iterative closest point implementation
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class IterativeClosestPointGUI implements PairwiseGUI
{
	public static int defaultModel = 2;
	public static boolean defaultRegularize = true;
	protected TransformationModelGUI model = null;

	protected IterativeClosestPointParameters parameters;

	@Override
	public IterativeClosestPointPairwise< InterestPoint > pairwiseMatchingInstance()
	{
		final IterativeClosestPointParameters ip = new IterativeClosestPointParameters( model.getModel() );
		return new IterativeClosestPointPairwise< InterestPoint >( ip );
	}

	@Override
	public MatcherPairwise< GroupedInterestPoint< ViewId > > pairwiseGroupedMatchingInstance()
	{
		final IterativeClosestPointParameters ip = new IterativeClosestPointParameters( model.getModel() );
		return new IterativeClosestPointPairwise< GroupedInterestPoint< ViewId > >( ip );
	}

	@Override
	public void addQuery( final GenericDialog gd )
	{
		gd.addChoice( "Transformation model", TransformationModelGUI.modelChoice, TransformationModelGUI.modelChoice[ defaultModel ] );
		gd.addCheckbox( "Regularize_model", defaultRegularize );
		gd.addSlider( "Maximal_distance for correspondence (px)", 0.25, 40.0, IterativeClosestPointParameters.maxDistance );
		gd.addNumericField( "Maximal_number of iterations", IterativeClosestPointParameters.maxIterations, 0 );
	}

	@Override
	public boolean parseDialog( final GenericDialog gd )
	{
		model = new TransformationModelGUI( defaultModel = gd.getNextChoiceIndex() );
		
		if ( defaultRegularize = gd.getNextBoolean() )
		{
			if ( !model.queryRegularizedModel() )
				return false;
		}

		final double maxDistance = IterativeClosestPointParameters.maxDistance = gd.getNextNumber();
		final int maxIterations = IterativeClosestPointParameters.maxIterations = (int)Math.round( gd.getNextNumber() );

		this.parameters = new IterativeClosestPointParameters( model.getModel(), maxDistance, maxIterations );

		return true;
	}

	@Override
	public IterativeClosestPointGUI newInstance() { return new IterativeClosestPointGUI(); }

	@Override
	public String getDescription() { return "Iterative closest-point (ICP, no invariance)";}

	@Override
	public TransformationModelGUI getMatchingModel() { return model; }

	@Override
	public double getMaxError() { return parameters.getMaxDistance(); }
}
