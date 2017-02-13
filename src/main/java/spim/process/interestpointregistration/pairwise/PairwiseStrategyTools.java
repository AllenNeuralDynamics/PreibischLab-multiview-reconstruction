package spim.process.interestpointregistration.pairwise;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import spim.process.interestpointregistration.pairwise.constellation.range.AllInRange;
import spim.process.interestpointregistration.pairwise.constellation.range.RangeComparator;

public class PairwiseStrategyTools
{
	public static < V > List< Pair< V, V > > allToAllRange(
			final List< ? extends V > views,
			final Collection< V > fixed,
			final Collection< ? extends Collection< V > > groups,
			final RangeComparator< V > rangeComparator )
	{
		// all pairs that need to be compared
		final ArrayList< Pair< V, V > > viewPairs = new ArrayList< Pair< V, V >>();

		for ( int a = 0; a < views.size() - 1; ++a )
			for ( int b = a + 1; b < views.size(); ++b )
			{
				final V viewIdA = views.get( a );
				final V viewIdB = views.get( b );

				// only compare those to views if not both are fixed and not
				// part of the same group
				if ( validPair( viewIdA, viewIdB, fixed, groups ) && rangeComparator.inRange( viewIdA, viewIdB ) )
					viewPairs.add( new ValuePair< V, V >( viewIdA, viewIdB ) );
			}

		return viewPairs;
	}

	public static < V > List< Pair< V, V > > allToAll(
			final List< ? extends V > views, final Collection< V > fixed,
			final Collection< ? extends Collection< V > > groups )
	{
		return allToAllRange( views, fixed, groups, new AllInRange< V >() );
	}

	public static < V > boolean validPair( final V viewIdA, final V viewIdB,
			final Collection< V > fixed,
			final Collection< ? extends Collection< V >> groups )
	{
		if ( fixed.contains( viewIdA ) && fixed.contains( viewIdB ) )
			return false;

		if ( oneSetContainsBoth( viewIdA, viewIdB, groups ) )
			return false;

		return true;
	}

	public static < V > boolean oneSetContainsBoth( final V viewIdA,
			final V viewIdB, final Collection< ? extends Collection< V >> sets )
	{
		for (final Collection< V > set : sets)
			if ( set.contains( viewIdA ) && set.contains( viewIdB ) )
				return true;

		return false;
	}

	public static void main( String[] args )
	{
		final ArrayList< ViewId > views = new ArrayList<>();

		for ( int tp = 0; tp < 10; ++tp )
			for ( int viewSetupId = 0; viewSetupId < 6; ++viewSetupId )
				views.add( new ViewId( tp, viewSetupId ) );

		final ArrayList< Pair< ViewId, ViewId > > pairs = new ArrayList<>();

		for ( int tp = 0; tp < 10; ++tp )
		{
		}
	}
}
