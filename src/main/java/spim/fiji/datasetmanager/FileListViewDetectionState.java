package spim.fiji.datasetmanager;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.security.auth.Destroyable;

import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import spim.fiji.datasetmanager.FileListDatasetDefinitionUtil.AngleInfo;
import spim.fiji.datasetmanager.FileListDatasetDefinitionUtil.ChannelInfo;
import spim.fiji.datasetmanager.FileListDatasetDefinitionUtil.CheckResult;
import spim.fiji.datasetmanager.FileListDatasetDefinitionUtil.TileInfo;

public class FileListViewDetectionState
{
	Boolean ambiguousAngleTile;
	Boolean ambiguousIllumChannel;
	
	Boolean groupedFormat;
	
	Map<Class<? extends Entity>, FileListDatasetDefinitionUtil.CheckResult> multiplicityMap;	
	Map<Class<? extends Entity>, Map<Object, List<Pair<File, Pair< Integer, Integer >>>>> accumulativeMap;	
	Map<Class<? extends Entity>, Map< Integer, List< Pair< File, Pair< Integer, Integer > > > >> idMap;	
	Map<Class<? extends Entity>, Map< Integer, Object>> detailMap;
	
	
	Map<Pair<File, Pair< Integer, Integer >>, Pair<Dimensions, VoxelDimensions>> dimensionMap;
	
	Map<String, Pair<File, Integer>> groupUsageMap;
	
	public FileListViewDetectionState()
	{
		multiplicityMap = new HashMap<>();		
		accumulativeMap = new HashMap<>();		
		idMap = new HashMap<>();		
		detailMap = new HashMap<>();
		groupUsageMap = new HashMap<>();
		
		for (Class<? extends Entity> cl : new Class[] {Angle.class, TimePoint.class, Illumination.class, Tile.class, Channel.class})
		{
			multiplicityMap.put( cl, CheckResult.SINGLE  );
			accumulativeMap.put( cl, new HashMap<>() );
			idMap.put( cl, new HashMap<>() );
			detailMap.put( cl, new HashMap<>() );
		}
		
		ambiguousAngleTile = false;
		ambiguousIllumChannel = false;
		
		groupedFormat = false;
		
		dimensionMap = new HashMap<>();
		
	}

	/**
	 * @param state the current state, after initial view detection
	 * @return pair min &amp; max number of channels in each (file, series)-combination
	 */
	public static Pair< Integer, Integer > getMinMaxNumChannelsIndexed(final FileListViewDetectionState state)
	{
		if ( state.accumulativeMap.get( Channel.class ).size() < 1 )
			return null;

		final List< Pair< File, Pair< Integer, Integer > > > channelSources = state.accumulativeMap.get( Channel.class )
				.values().iterator().next();
		final Map< Pair< File, Integer >, Integer > counts = new HashMap<>();
		for ( Pair< File, Pair< Integer, Integer > > channelSrc : channelSources )
		{
			Pair< File, Integer > key = new ValuePair< File, Integer >( channelSrc.getA(), channelSrc.getB().getA() );
			if ( !counts.containsKey( key ) )
				counts.put( key, 0 );
			counts.put( key, counts.get( key ) + 1 );
		}

		Integer min = counts.values().stream().reduce( Integer.MAX_VALUE, (x, y) -> Math.min( x, y ) );
		Integer max = counts.values().stream().reduce( Integer.MIN_VALUE, (x, y) -> Math.max( x, y ) );

		return new ValuePair< Integer, Integer >( min, max );
	}

	public static Pair< Integer, Integer > getMinMaxNumSeriesIndexed(final FileListViewDetectionState state)
	{
		if ( state.accumulativeMap.get( Tile.class ).size() < 1 )
			return null;

		final List< Pair< File, Pair< Integer, Integer > > > channelSources = state.accumulativeMap.get( Tile.class )
				.values().iterator().next();
		final Map< Pair< File, Integer >, Integer > counts = new HashMap<>();
		for ( Pair< File, Pair< Integer, Integer > > channelSrc : channelSources )
		{
			Pair< File, Integer > key = new ValuePair< File, Integer >( channelSrc.getA(), channelSrc.getB().getB() );
			if ( !counts.containsKey( key ) )
				counts.put( key, 0 );
			counts.put( key, counts.get( key ) + 1 );
		}

		Integer min = counts.values().stream().reduce( Integer.MAX_VALUE, (x, y) -> Math.min( x, y ) );
		Integer max = counts.values().stream().reduce( Integer.MIN_VALUE, (x, y) -> Math.max( x, y ) );

		return new ValuePair< Integer, Integer >( min, max );
	}

	public static boolean allVoxelSizesTheSame(FileListViewDetectionState state)
	{
		VoxelDimensions last = null;
		final Collection< Pair< Dimensions, VoxelDimensions > > sizes = state.getDimensionMap().values();
		for (final Pair< Dimensions, VoxelDimensions > size : sizes)
		{
			if (last == null)
				last = size.getB();
			if (!equalCalibration(last, size.getB() ))
				return false;
		}
		return true;
	}

	public static boolean equalCalibration(VoxelDimensions a, VoxelDimensions b)
	{
		if (a.numDimensions() != b.numDimensions())
			return false;
		if (!a.unit().equals( b.unit() ))
			return false;
		for (int d = 0; d<a.numDimensions(); d++)
			if (Math.abs( a.dimension( d ) - b.dimension( d ) ) > 1E-5)
				return false;
		return true;
	}

	public Map<Class<? extends Entity>, FileListDatasetDefinitionUtil.CheckResult> getMultiplicityMap()
	{
		return multiplicityMap;
	}

	public Boolean getAmbiguousAngleTile()
	{
		return ambiguousAngleTile;
	}

	public Boolean getAmbiguousIllumChannel()
	{
		return ambiguousIllumChannel;
	}
	
	public Map<Object, List< Pair< File, Pair< Integer, Integer > > >> getAccumulateMap(Class<? extends Entity> cl)
	{
		return accumulativeMap.get( cl );
	}
	

//	public Map< Integer, List< Pair< File, Pair< Integer, Integer > > > > getAccumulateTPMap()
//	{
//		return accumulateTPMap;
//	}
//
//	public Map< FileListDatasetDefinitionUtil.ChannelInfo, List< Pair< File, Pair< Integer, Integer > > > > getAccumulateChannelMap()
//	{
//		return accumulateChannelMap;
//	}
//
//	public Map< Integer, List< Pair< File, Pair< Integer, Integer > > > > getAccumulateIllumMap()
//	{
//		return accumulateIllumMap;
//	}
//
//	public Map< FileListDatasetDefinitionUtil.TileInfo, List< Pair< File, Pair< Integer, Integer > > > > getAccumulateTileMap()
//	{
//		return accumulateTileMap;
//	}
//
//	public Map< FileListDatasetDefinitionUtil.AngleInfo, List< Pair< File, Pair< Integer, Integer > > > > getAccumulateAngleMap()
//	{
//		return accumulateAngleMap;
//	}

	public Map< Pair< File, Pair< Integer, Integer > >, Pair< Dimensions, VoxelDimensions > > getDimensionMap()
	{
		return dimensionMap;
	}

	public void setAmbiguousAngleTile(Boolean ambiguousAngleTile)
	{
		this.ambiguousAngleTile = ambiguousAngleTile;
	}

	public void setAmbiguousIllumChannel(Boolean ambiguousIllumChannel)
	{
		this.ambiguousIllumChannel = ambiguousIllumChannel;
	}

	public Map< Class< ? extends Entity >, Map< Integer, List< Pair< File, Pair< Integer, Integer > > > > > getIdMap()
	{
		return idMap;
	}

	

	public Map< Class< ? extends Entity >, Map< Integer, Object > > getDetailMap()
	{
		return detailMap;
	}

	public Boolean getGroupedFormat()
	{
		return groupedFormat;
	}

	public void setGroupedFormat(Boolean groupedFormat)
	{
		this.groupedFormat = groupedFormat;
	}

	public Map< String, Pair< File, Integer > > getGroupUsageMap()
	{
		return groupUsageMap;
	}

	
	

}
