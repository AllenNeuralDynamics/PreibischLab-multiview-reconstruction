package spim.process.interestpointregistration.pairwise.constellation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import spim.process.interestpointregistration.pairwise.constellation.group.Group;

public class Subset< V >
{
	/**
	 *  all views contained in this subset
	 */
	Set< V > views;

	/**
	 * all pairs that need to be compared in that group
	 */
	List< Pair< V, V > > pairs;

	/**
	 * all groups that are part of this subset
	 */
	Set< Group< V > > groups;

	/**
	 * all views in this subset that are fixed
	 */
	Set< V > fixedViews;
	
	public Subset(
			final Set< V > views,
			final List< Pair< V, V > > pairs,
			final Set< Group< V > > groups )
	{
		this.views = views;
		this.pairs = pairs;
		this.groups = groups;
	}

	public List< Pair< V, V > > getPairs() { return pairs; }
	public Set< V > getViews() { return views; }
	public Set< Group< V > > getGroups() { return groups; }
	public Set< V > getFixedViews() { return fixedViews; }
	protected void setFixedViews( final Set< V > fixedViews ) { this.fixedViews = fixedViews; }

	/**
	 * Fix an additional list of views (removes pairs from subsets and sets list of fixed views)
	 * 
	 * @param fixedViews
	 * @return - the removed pairs due to fixed views
	 */
	public ArrayList< Pair< V, V > > fixViews( final List< V > fixedViews )
	{
		// which fixed views are actually present in this subset?
		final HashSet< V > fixedSubsetViews = filterPresentViews( views, fixedViews );

		// add the currently fixed ones
		fixedSubsetViews.addAll( getFixedViews() );

		// store the currently fixed views
		setFixedViews( fixedSubsetViews );

		return fixViews( fixedSubsetViews, pairs, groups );
	}

	/**
	 * Computes a list of grouped views that need to be compared, expressed as pairs.
	 * Single views are expressed as groups with cardinality 1. Then idea is that for
	 * pairwise comparisons, the algorithm groups views (e.g. all interestpoints) before
	 * running the actual algorithm.
	 */
	public List< Pair< Group< V >, Group< V > > > getGroupedPairs()
	{
		// all views contained in groups (those that existed + new ones with cardinality==1)
		final ArrayList< Group< V > > groups = createGroupsForAllViews( views, this.groups );

		// stupid, crazy example:
		// group0: v00, v01, v02, v03, v04
		// group1: v00, v10, v20, v30, v40
		//
		// group2: v22, v10
		//
		// pairs: v00 <> v22; v01 <> v10
		//
		// so what happens for the pair: v00 <> v22?
		// group0 vs group2 + group1 vs group2? <<< this one, you can merge later if you want
		//
		// so what happens for the pair: v01 <> v10?
		// group0 vs group1
		//
		// NOTE: these comparisons would be for nothing since the groups overlap (which is not tested)
		//       those unnecessary pairs would be ignored by the global optimization (or it crashes, not sure)

		final HashSet< Pair< Integer, Integer > > groupPairs = new HashSet<>();

		for ( final Pair< V, V > pair : pairs )
		{
			final V a = pair.getA();
			final V b = pair.getB();

			for ( int i = 0; i < groups.size(); ++i )
				for ( int j = 0; j < groups.size(); ++j )
				{
					final Group< V > groupA = groups.get( i );
					final Group< V > groupB = groups.get( j );

					if ( groupA.contains( a ) && groupB.contains( b ) )
						groupPairs.add( new ValuePair< Integer, Integer >( i, j ) );
				}
		}

		final List< Pair< Group< V >, Group< V > > > result = new ArrayList<>();
		for ( final Pair< Integer, Integer > groupPair : groupPairs )
			result.add( new ValuePair< Group< V >, Group< V > >(
							groups.get( groupPair.getA() ), groups.get( groupPair.getB() ) ) );

		return result;
	}

	/**
	 * Create a Set of groups that contains all views, with |group| >= 1.
	 * 
	 * @param views
	 * @param groups
	 * @return
	 */
	public static < V > ArrayList< Group< V > > createGroupsForAllViews(
			final Set< V > views,
			final Set< Group< V > > groups )
	{
		final ArrayList< Group< V > > groupsFull = new ArrayList<>();

		groupsFull.addAll( groups );

		for ( final V view : views )
		{
			// check if the view is contained in any of the groups
			boolean contains = false;

			for ( final Group< V > group : groups )
			{
				if ( group.contains( view ) )
				{
					contains = true;
					break;
				}
			}

			// if the view is not contained in any group, make a new one
			if ( !contains )
			{
				final Group< V > group = new Group<>( view );
				groupsFull.add( group );
			}
		}

		return groupsFull;
	}

	/**
	 * Checks which fixed views are present in the views list and puts them into a HashMap
	 * 
	 * @param views
	 * @param fixedViews
	 * @return
	 */
	public static < V > HashSet< V > filterPresentViews( final Set< V > views, final List< V > fixedViews )
	{
		// which of the fixed views are present in this subset?
		final HashSet< V > fixedSubsetViews = new HashSet<>();

		for ( final V fixedView : fixedViews )
			if ( views.contains( fixedView ) )
				fixedSubsetViews.add( fixedView );

		return fixedSubsetViews;
	}

	/**
	 * Fix an additional list of views (removes pairs from subsets and sets list of fixed views)
	 * 
	 * @param subset
	 * @param fixedViews
	 */
	public static < V > ArrayList< Pair< V, V > > fixViews(
			final HashSet< V > fixedSubsetViews,
			final List< Pair< V, V > > pairs,
			final Set< Group< V > > groups )
	{
		final ArrayList< Pair< V, V > > removed = new ArrayList<>();

		// remove pairwise comparisons between two fixed views
		for ( int i = pairs.size() - 1; i >= 0; --i )
		{
			final Pair< V, V > pair = pairs.get( i );

			// remove a pair if both views are fixed
			if ( fixedSubsetViews.contains( pair.getA() ) && fixedSubsetViews.contains( pair.getB() ) )
			{
				pairs.remove( i );
				removed.add( pair );
			}
		}

		// now check if any of the fixed views is part of a group
		// if so, no checks between groups where each contains at 
		// least one fixed tile are necessary
		final ArrayList< Group< V > > groupsWithFixedViews = new ArrayList<>();

		for ( final Group< V > group : groups )
		{
			for ( final V fixedView : fixedSubsetViews )
				if ( group.contains( fixedView ) )
				{
					groupsWithFixedViews.add( group );
					break;
				}
		}

		// if there is more than one group containing fixed views,
		// we need to remove all pairs between them
		if ( groupsWithFixedViews.size() > 1 )
		{
			for ( int i = pairs.size() - 1; i >= 0; --i )
			{
				final Pair< V, V > pair = pairs.get( i );

				final V a = pair.getA();
				final V b = pair.getB();

				// if a and b are present in any combination of fixed groups
				// they do not need to be compared
				boolean aPresent = false;
				boolean bPresent = false;

				for ( final Group< V > fixedGroup : groupsWithFixedViews )
				{
					aPresent |= fixedGroup.contains( a );
					bPresent |= fixedGroup.contains( b );
					if ( aPresent && bPresent )
						break;
				}

				if ( aPresent && bPresent )
				{
					pairs.remove( i );
					removed.add( pair );
				}
			}
		}

		return removed;
	}
}
