package spim.process.chromaticabberation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import ij.ImageJ;
import mpicbg.models.AffineModel3D;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.TranslationModel3D;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.XmlIoSpimData2;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.process.interestpointregistration.TransformationTools;
import spim.process.interestpointregistration.pairwise.MatcherPairwiseTools;
import spim.process.interestpointregistration.pairwise.PairwiseResult;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;
import spim.process.interestpointregistration.pairwise.methods.geometrichashing.GeometricHashingPairwise;
import spim.process.interestpointregistration.pairwise.methods.icp.IterativeClosestPointPairwise;
import spim.process.interestpointregistration.pairwise.methods.icp.IterativeClosestPointParameters;
import spim.process.interestpointregistration.pairwise.methods.ransac.RANSACParameters;
import spim.process.interestpointregistration.pairwise.methods.rgldm.RGLDMPairwise;
import spim.process.interestpointregistration.pairwise.methods.rgldm.RGLDMParameters;

public class DetectChromaticAbberation
{
	public static void main( String[] args ) throws SpimDataException, NotEnoughDataPointsException
	{
		new ImageJ();

		final SpimData2 spimData = new XmlIoSpimData2( "" ).load( "/Volumes/Data/SPIM/CLARITY/dataset_fullbrainsection.xml" );

		// select views to process
		final List< ViewId > viewIds = new ArrayList< ViewId >();
		viewIds.addAll( spimData.getSequenceDescription().getViewDescriptions().values() );
		SpimData2.filterMissingViews( spimData, viewIds );

		final ArrayList< Pair< Channel, Channel > > channelPairs = identifyChannelPairs( spimData, viewIds );

		final Map< ViewId, String > labelMap = new HashMap<>();
		for ( final ViewId viewId : viewIds )
			labelMap.put( viewId, "objects" );

		final Map< ViewId, List< InterestPoint > > interestpoints = new HashMap<>();

		for ( final ViewId viewId : viewIds )
			interestpoints.put( viewId,
				TransformationTools.getInterestPoints(
					viewId,
					spimData.getViewRegistrations().getViewRegistrations(),
					spimData.getViewInterestPoints().getViewInterestPoints(),
					labelMap,
					false ) );

		final RANSACParameters rp = new RANSACParameters().setMinInlierFactor( 20 );
		final RGLDMParameters dp = new RGLDMParameters( new TranslationModel3D(), 50, 2, 3, 1 );
		final IterativeClosestPointParameters ip = new IterativeClosestPointParameters( new TranslationModel3D(), 5.0, 100 );

		for ( final Pair< Channel, Channel > channelPair: channelPairs )
		{
			System.out.println( "Testing channel " + channelPair.getA().getName() + " vs " + channelPair.getB().getName()  );

			final ArrayList< Pair< ViewId, ViewId > > viewPairs = identifyViewIdPairs( spimData, viewIds, channelPair );

			for ( final Pair< ViewId, ViewId > viewPair : viewPairs )
				System.out.println( "comparing view " + Group.pvid( viewPair.getA() ) + " <=> " + Group.pvid( viewPair.getB() )  );

			final List< Pair< Pair< ViewId, ViewId >, PairwiseResult< InterestPoint > > > result =
					//MatcherPairwiseTools.computePairs( viewPairs, interestpoints, new IterativeClosestPointPairwise< InterestPoint >( ip ) );
					MatcherPairwiseTools.computePairs( viewPairs, interestpoints, new RGLDMPairwise< InterestPoint >( rp, dp ) );

			for ( final Pair< Pair< ViewId, ViewId >, PairwiseResult< InterestPoint > > p : result )
			{
				if ( p.getB().getInliers().size() > 0 )
				{
					//System.out.println( p.getB().getFullDesc() );

					final TranslationModel3D model = new TranslationModel3D();
					model.fit( p.getB().getInliers() );
					System.out.println( Util.printCoordinates( model.getTranslation() ) );
				}
			}
		}
	}

	public static < V extends ViewId > ArrayList< Pair< V, V > > identifyViewIdPairs(
			final SpimData2 spimData,
			final List< V > viewIds,
			final Pair< Channel, Channel > channelPair )
	{
		final SequenceDescription seq = spimData.getSequenceDescription();

		final Channel cA = channelPair.getA();
		final Channel cB = channelPair.getB();

		final ArrayList< Pair< V, V > > pairs = new ArrayList<>();

		for ( int v1 = 0; v1 < viewIds.size() - 1; ++v1 )
			for ( int v2 = v1 + 1; v2 < viewIds.size(); ++v2 )
			{
				final V view1 = viewIds.get( v1 );
				final V view2 = viewIds.get( v2 );

				final ViewDescription vd1 = seq.getViewDescription( view1 );
				final ViewDescription vd2 = seq.getViewDescription( view2 );

				final Channel c1 = vd1.getViewSetup().getChannel();
				final Channel c2 = vd2.getViewSetup().getChannel();

				if ( c1.getId() == cA.getId() && c2.getId() == cB.getId() || c1.getId() == cB.getId() && c2.getId() == cA.getId() )
				{
					boolean misMatch = false;

					for ( final Entity e1 : vd1.getViewSetup().getAttributes().values() )
						for ( final Entity e2 : vd2.getViewSetup().getAttributes().values() )
							if ( e1.getClass().equals( e2.getClass() ) && !e1.getClass().equals( Channel.class ) && e1.getId() != e2.getId() )
								misMatch = true;

					if ( !misMatch )
						pairs.add( new ValuePair<>( view1, view2 ) );
				}
			}

		return pairs;
	}

	/*
	 * This is very similar to Group.combineBy( Channel ), but returns pairs of channels (not all together)
	 */
	public static < V extends ViewId > ArrayList< Pair< Channel, Channel > > identifyChannelPairs( final SpimData2 spimData, final List< V > viewIds )
	{
		final SequenceDescription seq = spimData.getSequenceDescription();

		final HashSet< Channel > channelSet = new HashSet<>();

		for ( final V viewId : viewIds )
			channelSet.add( seq.getViewDescription( viewId ).getViewSetup().getChannel() );

		final ArrayList< Channel > channels = new ArrayList<>( channelSet );
		Collections.sort( channels );

		final ArrayList< Pair< Channel, Channel > > channelPairs = new ArrayList<>();

		for ( int c1 = 0; c1 < channels.size() - 1; ++c1 )
			for ( int c2 = c1 + 1; c2 < channels.size(); ++c2 )
				channelPairs.add( new ValuePair< Channel, Channel >( channels.get( c1 ), channels.get( c2 ) ) );

		return channelPairs;
	}

}
