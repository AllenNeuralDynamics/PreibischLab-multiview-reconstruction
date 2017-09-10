package spim.headless.registration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import mpicbg.models.TranslationModel3D;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.Translation3D;
import net.imglib2.util.ValuePair;
import spim.fiji.spimdata.boundingbox.BoundingBox;
import spim.fiji.spimdata.stitchingresults.PairwiseStitchingResult;
import spim.process.interestpointregistration.global.GlobalOptTwoRound;
import spim.process.interestpointregistration.global.convergence.ConvergenceStrategy;
import spim.process.interestpointregistration.global.convergence.IterativeConvergenceStrategy;
import spim.process.interestpointregistration.global.convergence.SimpleIterativeConvergenceStrategy;
import spim.process.interestpointregistration.global.linkremoval.MaxErrorLinkRemoval;
import spim.process.interestpointregistration.global.pointmatchcreating.PointMatchCreator;
import spim.process.interestpointregistration.global.pointmatchcreating.strong.ImageCorrelationPointMatchCreator;
import spim.process.interestpointregistration.global.pointmatchcreating.weak.MetaDataWeakLinkFactory;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;
import spim.process.interestpointregistration.pairwise.constellation.overlap.AllAgainstAllOverlap;

public class TestGlobalOptTwoRound
{
	public static void main( String[] args )
	{
		final ViewId view0 = new ViewId( 0, 0 );
		final ViewId view1 = new ViewId( 0, 1 );
		final ViewId view2 = new ViewId( 0, 2 );

		final Group< ViewId > group0 = new Group<>( view0 );
		final Group< ViewId > group1 = new Group<>( view1 );
		final Group< ViewId > group2 = new Group<>( view2 );

		final HashSet< Group< ViewId > > groups = new HashSet<>();
		groups.add( group0 );
		groups.add( group1 );
		groups.add( group2 );

		final ArrayList< ViewId > fixed = new ArrayList<>();
		fixed.add( view0 );

		final BoundingBox bb = new BoundingBox( new int[]{ 0, 0, 0 }, new int[]{ 511, 511, 511 } );
		final ArrayList< PairwiseStitchingResult< ViewId > > pairwiseResults = new ArrayList<>();

		pairwiseResults.add( new PairwiseStitchingResult<>( new ValuePair<>( group0, group1 ), bb,  new Translation3D( 100, 0, 0 ), 0.5 , 0.0) );
		pairwiseResults.add( new PairwiseStitchingResult<>( new ValuePair<>( group1, group2 ), bb,  new Translation3D( 0, 100.25, 0 ), 0.1, 0.0 ) );
		pairwiseResults.add( new PairwiseStitchingResult<>( new ValuePair<>( group0, group2 ), bb,  new Translation3D( 100, 100.5, 0 ), 0.1, 0.0 ) );

		final IterativeConvergenceStrategy cs = new SimpleIterativeConvergenceStrategy( 10.0, 10.0, 10.0 );
		final PointMatchCreator pmc = new ImageCorrelationPointMatchCreator( pairwiseResults, 0.3 );
		
		final HashMap<ViewId, ViewRegistration> vrMap = new HashMap<>();
		
		AffineTransform3D tr0 = new AffineTransform3D();
		tr0.translate( new double[] {0.0, 0.0, 0.0} );
		tr0 = tr0.copy();
		new ViewRegistration( 0, 0, tr0 );
		vrMap.put( view0, new ViewRegistration( 0, 0, tr0 ) );
		
		AffineTransform3D tr1 = new AffineTransform3D();
		tr1.translate( new double[] {0.0, 300.0, 0.0} );
		tr1 = tr1.copy();
		new ViewRegistration( 0, 1, tr1 );
		vrMap.put( view1, new ViewRegistration( 0, 1, tr1 ) );
		
		AffineTransform3D tr2 = new AffineTransform3D();
		tr2.translate( new double[] {300.0, 300.0, 0.0} );
		tr2 = tr2.copy();
		new ViewRegistration( 0, 2, tr2 );
		vrMap.put( view2, new ViewRegistration( 0, 2, tr2 ) );

		final HashMap< ViewId, AffineTransform3D > computeResults = GlobalOptTwoRound.compute(
				new TranslationModel3D(),
				pmc,
				cs,
				new MaxErrorLinkRemoval(),
				new MetaDataWeakLinkFactory( vrMap, new AllAgainstAllOverlap< ViewId >( bb.numDimensions() ) ),
				new ConvergenceStrategy( Double.MAX_VALUE ),
				fixed,
				groups );
		
		computeResults.forEach( ( k, v) -> {
			System.out.println( k + ": " + v );
		});
	}
}
