package org.embl.mobie.viewer.display;

import org.embl.mobie.viewer.annotate.AnnotatedMaskAdapter;
import org.embl.mobie.viewer.annotate.AnnotatedMaskTableRow;
import org.embl.mobie.viewer.bdv.view.AnnotatedMaskSliceView;
import org.embl.mobie.viewer.source.StorageLocation;
import org.embl.mobie.viewer.table.TableDataFormat;

import java.util.*;

public class AnnotatedMaskDisplay extends AnnotatedRegionDisplay< AnnotatedMaskTableRow >
{
	// Serialization
	protected Map< String, List< String > > sources;
	protected List< String > selectedAnnotationIds;
	protected Map< TableDataFormat, StorageLocation > tableData;

	// Runtime
	public transient AnnotatedMaskAdapter annotatedMaskAdapter;
	public transient AnnotatedMaskSliceView< AnnotatedMaskTableRow > sliceView;

	// Getters for the serialised fields
	public List< String > getSelectedAnnotationIds()
	{
		return selectedAnnotationIds;
	}

	public String getTableDataFolder( TableDataFormat tableDataFormat )
	{
		return tableData.get( tableDataFormat ).relativePath;
	}

	public Map< String, List< String > > getAnnotationIdToSources()
	{
		return sources;
	}

	@Override
	public List< String > getSources()
	{
		final ArrayList< String > allSources = new ArrayList<>();
		for ( List< String > sources : this.sources.values() )
			allSources.addAll( sources );
		return allSources;
	}

	public AnnotatedMaskDisplay() {}

	public AnnotatedMaskDisplay( String name, double opacity, Map< String, List< String > > sources, String lut, String colorByColumn, Double[] valueLimits, List< String > selectedSegmentIds, boolean showScatterPlot, String[] scatterPlotAxes, List< String > tables )
	{
		this.name = name;
		this.opacity = opacity;
		this.sources = sources;
		this.lut = lut;
		this.colorByColumn = colorByColumn;
		this.valueLimits = valueLimits;
		this.selectedAnnotationIds = selectedSegmentIds;
		this.showScatterPlot = showScatterPlot;
		this.scatterPlotAxes = scatterPlotAxes;
		this.tables = tables;
	}

	/**
	 * Create a serializable copy
	 *
	 * @param annotatedMaskDisplay
	 */
	public AnnotatedMaskDisplay( AnnotatedMaskDisplay annotatedMaskDisplay )
	{
		fetchCurrentSettings( annotatedMaskDisplay );

		this.sources = new HashMap<>();
		this.sources.putAll( annotatedMaskDisplay.sources );

		Set< AnnotatedMaskTableRow > currentSelectedRows = annotatedMaskDisplay.selectionModel.getSelected();
		if ( currentSelectedRows != null && currentSelectedRows.size() > 0 ) {
			ArrayList<String> selectedIds = new ArrayList<>();
			for ( AnnotatedMaskTableRow row : currentSelectedRows ) {
				selectedIds.add( row.getTimepoint() + ";" + row.getName() );
			}
			this.selectedAnnotationIds = selectedIds;
		}

		this.tableData = new HashMap<>();
		this.tableData.putAll( annotatedMaskDisplay.tableData );
	}
}