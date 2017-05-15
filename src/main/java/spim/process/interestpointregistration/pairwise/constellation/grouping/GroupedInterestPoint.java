package spim.process.interestpointregistration.pairwise.constellation.grouping;

import spim.fiji.spimdata.interestpoints.InterestPoint;

/**
 * Single interest point, extends InterestPoint to remember where it came from for grouping
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class GroupedInterestPoint< V > extends InterestPoint
{
	private static final long serialVersionUID = -7473168262289156212L;
	final V view;

	public GroupedInterestPoint( final V view, final int id, final double[] l )
	{
		super( id, l );

		this.view = view;
	}

	public V getV() { return view; }

	@Override
	public GroupedInterestPoint< V > duplicate() { return clone(); }

	@Override
	public GroupedInterestPoint< V > clone() { return new GroupedInterestPoint< V >( this.view, this.id, this.l.clone() ); }

	@Override
	public GroupedInterestPoint< V > newInstance( final int id, final double[] l ) { return new GroupedInterestPoint< V > ( null, id, l ); }

	@Override
	public boolean equals( final Object obj )
	{
		if ( this == obj )
			return true;
		if ( obj == null )
			return false;
		if ( getClass() != obj.getClass() )
			return false;
		GroupedInterestPoint< ? > other = (GroupedInterestPoint< ? >) obj;
		if ( view == null )
		{
			if ( other.view != null )
				return false;
		}
		else if ( !view.equals( other.view ) )
		{
			return false;
		}
		else if ( id != other.id )
		{
			return false;
		}

		return true;
	}
}
