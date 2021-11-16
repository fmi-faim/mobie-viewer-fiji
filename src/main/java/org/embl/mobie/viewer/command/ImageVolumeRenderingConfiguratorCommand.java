package org.embl.mobie.viewer.command;

import bdv.viewer.SourceAndConverter;
import org.embl.mobie.viewer.volume.ImageVolumeViewer;
import org.embl.mobie.viewer.volume.SegmentsVolumeViewer;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.services.SourceAndConverterServices;

@Plugin(type = BdvPlaygroundActionCommand.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Display>Configure Image Volume Rendering")
public class ImageVolumeRenderingConfiguratorCommand implements BdvPlaygroundActionCommand
{
	@Parameter
	SourceAndConverter[] sourceAndConverters;

	@Parameter ( label = "Resolution X [um]")
	double sx;

	@Parameter ( label = "Resolution Y [um]")
	double sy;

	@Parameter ( label = "Resolution Z [um]")
	double sz;

	@Parameter ( label = "Repaint all images")
	boolean repaint;

	@Override
	public void run()
	{
		setVoxelSpacing( sourceAndConverters, new double[]{ sx, sy, sz }, repaint );
	}

	public static void setVoxelSpacing( SourceAndConverter[] sourceAndConverters, double[] voxelSpacing, boolean repaint )
	{
		final SourceAndConverterService sacService = ( SourceAndConverterService ) SourceAndConverterServices.getSourceAndConverterService();

		for ( SourceAndConverter sourceAndConverter : sourceAndConverters )
		{
			final ImageVolumeViewer volumeViewer = ( ImageVolumeViewer ) sacService.getMetadata( sourceAndConverter, ImageVolumeViewer.class.getName() );
			if ( volumeViewer != null )
			{
				volumeViewer.setVoxelSpacing( voxelSpacing );
				if ( repaint )
					volumeViewer.updateView();
			}
		}
	}
}
