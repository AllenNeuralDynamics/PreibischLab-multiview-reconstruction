package spim.fiji.plugin.boundingbox;

import java.awt.Choice;
import java.awt.Label;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;

import ij.gui.GenericDialog;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.plugin.util.GUIHelper;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.boundingbox.BoundingBox;

public class DefinedBoundingBoxGUI extends BoundingBoxGUI
{
	public static int defaultBoundingBox = 0;

	public DefinedBoundingBoxGUI( final SpimData2 spimData, final List<ViewId> viewIdsToProcess )
	{
		super( spimData, viewIdsToProcess );
	}

	@Override
	protected boolean setUpDefaultValues( final int[] rangeMin, final int[] rangeMax )
	{
		if ( spimData.getBoundingBoxes().getBoundingBoxes().size() == 0 )
		{
			IOFunctions.println( "No bounding boxes pre-defined." );
			return false;
		}

		if ( !findRange( spimData, viewIdsToProcess, rangeMin, rangeMax ) )
			return false;

		final GenericDialog gd1 = new GenericDialog( "Pre-defined Bounding Box" );

		final String[] boundingBoxes = new String[ spimData.getBoundingBoxes().getBoundingBoxes().size() ];

		for ( int i = 0; i < boundingBoxes.length; ++i )
			boundingBoxes[ i ] = spimData.getBoundingBoxes().getBoundingBoxes().get( i ).getTitle();

		if ( defaultBoundingBox >= boundingBoxes.length )
			defaultBoundingBox = 0;

		gd1.addChoice( "Bounding_box_title", boundingBoxes, boundingBoxes[ defaultBoundingBox ] );
		final Choice choice = (Choice)gd1.getChoices().lastElement();

		gd1.addMessage( "" );
		gd1.addMessage( "BoundingBox size: ???x???x??? pixels", GUIHelper.mediumstatusfont );
		final Label l1 = (Label)gd1.getMessage();

		gd1.addMessage( "BoundingBox offset: ???x???x??? pixels", GUIHelper.mediumstatusfont );
		final Label l2 = (Label)gd1.getMessage();

		addListeners( gd1, choice, l1, l2 );

		gd1.showDialog();

		if ( gd1.wasCanceled() )
			return false;

		final BoundingBox bb = spimData.getBoundingBoxes().getBoundingBoxes().get( defaultBoundingBox = gd1.getNextChoiceIndex() );

		this.min = bb.getMin().clone();
		this.max = bb.getMax().clone();

		if ( defaultMin == null )
			defaultMin = min.clone();

		if ( defaultMax == null )
			defaultMax = max.clone();

		return true;
	}

	@Override
	public DefinedBoundingBoxGUI newInstance( final SpimData2 spimData, final List< ViewId > viewIdsToProcess )
	{
		return new DefinedBoundingBoxGUI( spimData, viewIdsToProcess );
	}

	@Override
	public String getDescription()
	{
		return "Modify pre-defined Bounding Box";
	}

	protected void addListeners(
			final GenericDialog gd,
			final Choice choice,
			final Label label1,
			final Label label2 )
	{
		choice.addItemListener( new ItemListener()
		{
			@Override
			public void itemStateChanged(ItemEvent e)
			{
				update( spimData, choice, label1, label2 );
			}
		});

		update( spimData, choice, label1, label2 );
	}

	protected final static void update( final SpimData2 spimData, final Choice choice, final Label label1, final Label label2 )
	{
		final int index = choice.getSelectedIndex();
		final BoundingBox bb = spimData.getBoundingBoxes().getBoundingBoxes().get( index );

		label1.setText( "Bounding Box size: " + bb.dimension( 0 ) + "x" + bb.dimension( 1 ) + "x" + bb.dimension( 2 ) + " pixels" );
		label2.setText( "Bounding Box offset: " + bb.min( 0 ) + "x" + bb.min( 1 ) + "x" + bb.min( 2 ) + " pixels" );
	}

	@Override
	protected boolean allowModifyDimensions() { return true; }
}
