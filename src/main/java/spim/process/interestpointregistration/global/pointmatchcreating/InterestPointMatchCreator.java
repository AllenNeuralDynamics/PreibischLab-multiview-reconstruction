package spim.process.interestpointregistration.global.pointmatchcreating;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import mpicbg.models.Model;
import mpicbg.models.PointMatch;
import mpicbg.models.Tile;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.mpicbg.PointMatchGeneric;
import net.imglib2.util.Pair;
import spim.process.interestpointregistration.pairwise.PairwiseResult;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class InterestPointMatchCreator implements PointMatchCreator
{
	final List< ? extends Pair< ? extends Pair< ViewId, ViewId >, ? extends PairwiseResult< ? > > > pairs;

	public InterestPointMatchCreator( final List< ? extends Pair< ? extends Pair< ViewId, ViewId >, ? extends PairwiseResult< ? > > > pairs )
	{
		this.pairs = pairs;
	}

	@Override
	public HashSet< ViewId > getAllViews()
	{
		final HashSet< ViewId > tmpSet = new HashSet<>();

		for ( Pair< ? extends Pair< ViewId, ViewId >, ? extends PairwiseResult< ? > > pair : pairs )
		{
			tmpSet.add( pair.getA().getA() );
			tmpSet.add( pair.getA().getB() );
		}

		return tmpSet;
	}

	@Override
	public < M extends Model< M > > void assignPointMatches(
			final HashMap< ViewId, Tile< M > > tileMap,
			final ArrayList< Group< ViewId > > groups,
			final Collection< ViewId > fixedViews )
	{
		for ( Pair< ? extends Pair< ViewId, ViewId >, ? extends PairwiseResult< ? > > pair : pairs )
			addPointMatches( pair.getB().getInliers(), tileMap.get( pair.getA().getA() ), tileMap.get( pair.getA().getB() ) );
	}

	@Override
	public < M extends Model< M > > void assignWeights(
			final HashMap< ViewId, Tile< M > > tileMap,
			final ArrayList< Group< ViewId > > groups,
			final Collection< ViewId > fixedViews )
	{
		assignWeights( pairs, groups, tileMap );
	}

	public static void addPointMatches( final List< ? extends PointMatchGeneric< ? > > correspondences, final Tile< ? > tileA, final Tile< ? > tileB )
	{
		final ArrayList< PointMatch > pm = new ArrayList<>();
		pm.addAll( correspondences );

		if ( correspondences.size() > 0 )
		{
			tileA.addMatches( pm );
			tileB.addMatches( PointMatch.flip( pm ) );
			tileA.addConnectedTile( tileB );
			tileB.addConnectedTile( tileA );
		}
	}

	public static < M extends Model< M > >  void assignWeights(
			final List< ? extends Pair< ? extends Pair< ViewId, ViewId >, ? extends PairwiseResult< ? > > > pairs,
			final ArrayList< Group< ViewId > > groups,
			final HashMap< ViewId, Tile< M > > tileMap )
	{
		final HashMap< Group< ViewId >, Integer > groupCount = new HashMap<>();
		final HashMap< ViewId, Integer > viewCount = new HashMap<>();
		final HashMap< ViewId, Group< ViewId > > viewGroupAssign = new HashMap<>();

		for ( final Group< ViewId > group : groups )
			groupCount.put( group, 0 );

		for ( final ViewId viewId : tileMap.keySet() )
			viewCount.put( viewId, 0 );

		// find out inliers per view, and sum of inliers per group
		for ( Pair< ? extends Pair< ViewId, ViewId >, ? extends PairwiseResult< ? > > pair : pairs )
		{
			final ViewId vA = pair.getA().getA();
			final ViewId vB = pair.getA().getB();

			final int numInliers = pair.getB().getInliers().size();

			viewCount.put( vA, viewCount.get( vA ) + numInliers );
			viewCount.put( vB, viewCount.get( vB ) + numInliers );

			for ( final Group< ViewId > group : groups )
			{
				if ( group.contains( vA ) )
				{
					groupCount.put( group, groupCount.get( group ) + numInliers );
					viewGroupAssign.put( vA, group );
				}

				if ( group.contains( vB ) )
				{
					groupCount.put( group, groupCount.get( group ) + numInliers );
					viewGroupAssign.put( vB, group );
				}
			}
		}

		final HashMap< ViewId, Double > ratio = new HashMap<>();
		final HashMap< Group< ViewId >, Double > maxGroupRatio = new HashMap<>();

		// find the ratio (inliers/groupInliers) per view, or 1.0 if it is not part of a group (i.e. one view per group)
		for ( final ViewId viewId : tileMap.keySet() )
		{
			final Group< ViewId > group = viewGroupAssign.get( viewId );

			if ( group != null )
			{
				final double numCorr = viewCount.get( viewId );
				final double numCorrGroup = groupCount.get( group );
				ratio.put( viewId, numCorr / numCorrGroup );

				double maxRatio;

				if ( maxGroupRatio.containsKey( group ) )
					maxRatio = maxGroupRatio.get( group );
				else
					maxRatio = -1;

				maxGroupRatio.put( group, Math.max( maxRatio, numCorr / numCorrGroup ) );
			}
			else
			{
				ratio.put( viewId, 1.0 );
			}
		}

		final ArrayList< ViewId > views = new ArrayList<>();
		views.addAll( tileMap.keySet() );
		Collections.sort( views );

		// assign a value that reflects how much lower the ratio is per view
		// e.g. view0 = 0.5, view1 = 0.25
		// means view0 = 0.5 and view1 = 2
		for ( final ViewId viewId : views )
		{
			final Group< ViewId > group = viewGroupAssign.get( viewId );
			double maxRatio = 1;
			if ( group != null )
				maxRatio = maxGroupRatio.get( group );

			System.out.println( Group.pvid( viewId ) + ": " + ratio.get( viewId ) + " of "  + maxRatio + " >>> " + ( maxRatio/ratio.get( viewId ) ) );

			ratio.put( viewId, ( maxRatio/ratio.get( viewId ) ) );
		}

		// assign the max of each value to the pointmatches between two views
		for ( final Pair< ? extends Pair< ViewId, ViewId >, ? extends PairwiseResult< ? > > pair : pairs )
		{
			final double ratioA = ratio.get( pair.getA().getA() );
			final double ratioB = ratio.get( pair.getA().getB() );

			final double weight = Math.max( ratioA, ratioB );

			for ( final PointMatchGeneric< ? > pm : pair.getB().getInliers() )
				pm.setWeight( 0, weight );

			//System.out.println( Group.pvid( pair.getA().getA() ) + "<->" + Group.pvid( pair.getA().getB() ) + ": " + weight );
		}
	}
}
