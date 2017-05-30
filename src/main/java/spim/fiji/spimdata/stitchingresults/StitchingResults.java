package spim.fiji.spimdata.stitchingresults;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;


public class StitchingResults
{
	Map<Pair<Group<ViewId>, Group<ViewId>>, PairwiseStitchingResult<ViewId>> pairwiseResults;
	Map<ViewId, AffineGet> globalShifts;
	
	public StitchingResults()
	{
		pairwiseResults = new HashMap<>();
		globalShifts = new HashMap<>();
	}

	public Map< Pair< Group<ViewId>, Group<ViewId> >, PairwiseStitchingResult<ViewId> > getPairwiseResults() { return pairwiseResults; }
	
	public Map< ViewId, AffineGet > getGlobalShifts() { return globalShifts;	}
	
	/**
	 * save the PairwiseStitchingResult for a pair of ViewIds, using the sorted ViewIds as a key
	 * use this method to ensure consistency of the pairwiseResults Map 
	 * @param pair
	 * @param res
	 */
	public void setPairwiseResultForPair(Pair<Group<ViewId>, Group<ViewId>> pair, PairwiseStitchingResult<ViewId> res )
	{
		//Pair< Set<ViewId>, Set<ViewId> > key = pair.getA().compareTo( pair.getB() ) < 0 ? pair : new ValuePair<>(pair.getB(), pair.getA());
		pairwiseResults.put( pair, res );
	}	
	public PairwiseStitchingResult<ViewId> getPairwiseResultsForPair(Pair<Group<ViewId>, Group<ViewId>> pair)
	{
		//Pair< ViewId, ViewId > key = pair.getA().compareTo( pair.getB() ) < 0 ? pair : new ValuePair<>(pair.getB(), pair.getA());
		return pairwiseResults.get( pair );
	}
	public void removePairwiseResultForPair(Pair<Set<ViewId>, Set<ViewId>> pair)
	{
		//Pair< ViewId, ViewId > key = pair.getA().compareTo( pair.getB() ) < 0 ? pair : new ValuePair<>(pair.getB(), pair.getA());
		pairwiseResults.remove( pair );
	}
	
	
	public ArrayList< PairwiseStitchingResult<ViewId> > getAllPairwiseResultsForViewId(Set<ViewId> vid)
	{
		ArrayList< PairwiseStitchingResult<ViewId> > res = new ArrayList<>();
		for (Pair< Group<ViewId>, Group<ViewId> > p : pairwiseResults.keySet())
		{
			if (p.getA().getViews().equals( vid ) || p.getB().getViews().equals( vid )){
				res.add( pairwiseResults.get( p ) );
			}
		}
		return res;
	}
	
	
	public ArrayList< Double > getErrors(Set<ViewId> vid)
	{
		List<PairwiseStitchingResult<ViewId>> psrs = getAllPairwiseResultsForViewId( vid );
		ArrayList< Double > res = new ArrayList<>();
		for (PairwiseStitchingResult <ViewId>psr : psrs)
		{
			if (globalShifts.containsKey( psr.pair().getA()) && globalShifts.containsKey( psr.pair().getB() ))
			{
				double[] vGlobal1 = new double[3];
				double[] vGLobal2 = new double[3];
				double[] vPairwise = new double[3];
				globalShifts.get( psr.pair().getA() ).apply( vGlobal1, vGlobal1 );
				globalShifts.get( psr.pair().getB() ).apply( vGLobal2, vGLobal2 );
				psr.getTransform().apply( vPairwise, vPairwise );
				double[] relativeGlobal = VectorUtil.getVectorDiff( vGlobal1, vGLobal2 );
				res.add( new Double(VectorUtil.getVectorLength(  VectorUtil.getVectorDiff( relativeGlobal, vPairwise ) )) );
			}
				
		}
		return res;		
	}
	
	
	public double getAvgCorrelation(Set<ViewId> vid)
	{
		double sum = 0.0;
		int count = 0;
		for (PairwiseStitchingResult<ViewId> psr : pairwiseResults.values())
		{
			if (vid.equals( psr .pair().getA().getViews()) || vid.equals( psr .pair().getB().getViews()))
			{
				sum += psr.r();
				count++;
			}
							
		}
		
		if (count == 0)
			return 0;
		else
			return sum/count;
	}
	
	public static void main(String[] args)
	{
//		StitchingResults sr = new StitchingResults();
//		sr.getPairwiseResults().put( new ValuePair<>(new ViewId( 0, 0 ), new ViewId( 0, 1 )), null );
//		sr.getPairwiseResults().put( new ValuePair<>(new ViewId( 0, 0 ), new ViewId( 0, 1 )), null );
//		sr.getPairwiseResults().put( new ValuePair<>(new ViewId( 0, 1 ), new ViewId( 0, 2 )), null );
//		
//		ArrayList< PairwiseStitchingResult<ViewId> > psr = sr.getAllPairwiseResultsForViewId( new ViewId( 0, 0 ) );
//		System.out.println( psr.size() );
	}
	
	
}
