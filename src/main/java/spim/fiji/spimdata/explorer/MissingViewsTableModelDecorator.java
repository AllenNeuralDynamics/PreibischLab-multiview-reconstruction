package spim.fiji.spimdata.explorer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.event.TableModelListener;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;

public class MissingViewsTableModelDecorator < AS extends AbstractSpimData< ? > > implements ISpimDataTableModel< AS >
{

	private ISpimDataTableModel<AS> decorated;
	final ArrayList< String > columnNames;
	
	public MissingViewsTableModelDecorator(ISpimDataTableModel< AS > decorated)
	{
		this.decorated = decorated;
		columnNames = new ArrayList<>();
		columnNames.add( "Views present?" );
	}
	
	@Override
	public int getRowCount() {
		return decorated.getRowCount();
	}

	@Override
	public int getColumnCount() {
		return decorated.getColumnCount() + columnNames.size();
	}

	@Override
	public String getColumnName(int columnIndex) {
		// TODO Auto-generated method stub
		if (columnIndex < decorated.getColumnCount())
			return decorated.getColumnName(columnIndex);
		else
			return columnNames.get( columnIndex - decorated.getColumnCount() );
	}

	@Override
	public Class<?> getColumnClass(int columnIndex) {
		if (columnIndex < decorated.getColumnCount())
			return decorated.getColumnClass(columnIndex);
		else
			return String.class;
	}

	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex)
	{
		return false;
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex)
	{
		if (columnIndex < decorated.getColumnCount())
			return decorated.getValueAt( rowIndex, columnIndex );
		else
		{
			final List< BasicViewDescription< ? > > vds = getElements().get( rowIndex );

			if ( vds.size() == 1 )
			{
				return Boolean.toString( vds.get( 0 ).isPresent() );
			}
			else
			{
				int present = 0;

				for ( final BasicViewDescription< ? > vd : vds )
					if ( vd.isPresent() )
						++present;

				return present + "/" + vds.size();
			}
		}
	}

	@Override
	public void setValueAt(Object aValue, int rowIndex, int columnIndex){}


	@Override
	public int getSpecialColumn(spim.fiji.spimdata.explorer.ISpimDataTableModel.SpecialColumnType type)
	{
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public ExplorerWindow< AS, ? > getPanel()
	{
		return decorated.getPanel();
	}

	@Override
	public void clearSortingFactors(){decorated.clearSortingFactors();}

	@Override
	public void addSortingFactor(Class< ? extends Entity > factor){decorated.addSortingFactor( factor );}

	@Override
	public void clearGroupingFactors(){decorated.clearGroupingFactors();}

	@Override
	public void addGroupingFactor(Class< ? extends Entity > factor){decorated.addGroupingFactor( factor );}

	@Override
	public void clearFilters(){decorated.clearFilters();}

	@Override
	public void addFilter(Class< ? extends Entity > cl, List< ? extends Entity > instances){decorated.addFilter( cl, instances );}

	@Override
	public List< List< BasicViewDescription< ? > > > getElements(){return decorated.getElements();}

	@Override
	public void sortByColumn(int column)
	{
		if (column < decorated.getColumnCount())
			decorated.sortByColumn( column );
	}

	@Override
	public void setColumnClasses(List< Class< ? extends Entity > > columnClasses){decorated.setColumnClasses( columnClasses );}

	@Override
	public void addTableModelListener(TableModelListener l) {decorated.addTableModelListener(l);}

	@Override
	public void removeTableModelListener(TableModelListener l) {decorated.removeTableModelListener(l);}

	@Override
	public Set< Class< ? extends Entity > > getGroupingFactors(){return decorated.getGroupingFactors();}

	@Override
	public Map< Class< ? extends Entity >, List< ? extends Entity > > getFilters()
	{
		return decorated.getFilters();
	}

}
