package spim.fiji.spimdata.explorer.popup;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Date;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Interval;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.boundingbox.BoundingBox;
import spim.fiji.spimdata.explorer.ExplorerWindow;
import spim.process.boundingbox.BoundingBoxTools;
import spim.process.fusion.FusionTools;
import spim.process.fusion.FusionTools.ImgDataType;

public class DisplayFusedImagesPopup extends JMenu implements ExplorerWindowSetable
{
	public static int[] quickDownsampling = new int[]{ 1, 2, 3, 4, 8, 16 };
	public static int defaultCache = 0;
	public static int[] cellDim = new int[]{ 100, 100, 1 };
	public static int maxCacheSize = 100000;

	public static int defaultInterpolation = 1;
	public static boolean defaultUseBlending = true;

	private static final long serialVersionUID = -4895470813542722644L;

	ExplorerWindow< ?, ? > panel = null;

	public DisplayFusedImagesPopup()
	{
		super( "Quick Display Transformed/Fused Image(s)" );

		final JMenu boundingBoxes = this;

		// populate with the current available boundingboxes
		this.addMenuListener( new MenuListener()
		{
			@Override
			public void menuSelected( MenuEvent e )
			{
				if ( panel != null )
				{
					boundingBoxes.removeAll();

					final SpimData2 spimData = (SpimData2)panel.getSpimData();

					final ArrayList< ViewId > views = new ArrayList<>();
					views.addAll( ApplyTransformationPopup.getSelectedViews( panel ) );

					// filter not present ViewIds
					SpimData2.filterMissingViews( panel.getSpimData(), views );

					for ( final BoundingBox bb : BoundingBoxTools.getAllBoundingBoxes( spimData, views, true ) )
					{
						final JMenu downsampleOptions = new JMenu( bb.getTitle() + " [" + bb.dimension( 0 ) + "x" + bb.dimension( 1 ) + "x" + bb.dimension( 2 ) + "px]" );

						for ( final int ds : quickDownsampling )
						{
							final JMenuItem fused;
							final double downsample;

							if ( ds == 1 )
							{
								fused = new JMenuItem( "Not downsampled" );
								downsample = Double.NaN;
							}
							else
							{
								fused = new JMenuItem( "Downsampled " + ds + "x" );
								downsample = ds;
							}

							fused.addActionListener( new DisplayVirtualFused( spimData, views, bb, downsample, ImgDataType.values()[ defaultCache ] ) );
							downsampleOptions.add( fused );
						}
						boundingBoxes.add( downsampleOptions );
					}

					boundingBoxes.add( new Separator() );

					final JMenuItem[] items = new JMenuItem[ FusionTools.imgDataTypeChoice.length ];

					for ( int i = 0; i < items.length; ++i )
					{
						final JMenuItem item = new JMenuItem( FusionTools.imgDataTypeChoice[ i ] );

						if ( i == defaultCache )
							item.setForeground( Color.RED );
						else
							item.setForeground( Color.GRAY );

						items[ i ] = item;
					}

					for ( int i = 0; i < items.length; ++i )
					{
						final JMenuItem item = items[ i ];
						item.addActionListener( new ChangeCacheState( items, i ) );
						boundingBoxes.add( item );
					}

				}
			}

			@Override
			public void menuDeselected( MenuEvent e ) {}

			@Override
			public void menuCanceled( MenuEvent e ) {}
		} );
	}

	@Override
	public JMenuItem setExplorerWindow( final ExplorerWindow< ?, ? > panel )
	{
		this.panel = panel;

		return this;
	}

	public class DisplayVirtualFused implements ActionListener
	{
		final SpimData spimData;
		final ArrayList< ViewId > views;
		final Interval bb;
		final double downsampling;
		final ImgDataType imgType;

		public DisplayVirtualFused( final SpimData spimData, final ArrayList< ViewId > views, final Interval bb, final double downsampling, final ImgDataType imgType )
		{
			this.spimData = spimData;
			this.views = views;
			this.bb = bb;
			this.downsampling = downsampling;
			this.imgType = imgType;
		}

		@Override
		public void actionPerformed( final ActionEvent e )
		{
			if ( panel == null )
			{
				IOFunctions.println( "Panel not set for " + this.getClass().getSimpleName() );
				return;
			}

			new Thread( new Runnable()
			{
				@Override
				public void run()
				{
					IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Fusing " + views.size() + ", downsampling=" + downsampling + ", caching strategy=" + imgType );
					FusionTools.display( FusionTools.fuseVirtual( spimData, views, defaultUseBlending, false, defaultInterpolation, bb, downsampling ), imgType ).show();
				}
			} ).start();
		}
	}

	public class ChangeCacheState implements ActionListener
	{
		final JMenuItem[] items;
		final int myState;

		public ChangeCacheState( final JMenuItem[] items, final int myState )
		{
			this.items = items;
			this.myState = myState;
		}

		@Override
		public void actionPerformed( final ActionEvent e )
		{
			for ( int i = 0; i < items.length; ++i )
			{
				if ( i == myState )
					items[ i ].setForeground( Color.RED );
				else
					items[ i ].setForeground( Color.GRAY );
			}

			defaultCache = myState;
		}
	}
}
