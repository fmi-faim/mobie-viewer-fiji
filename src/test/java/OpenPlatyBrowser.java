import de.embl.cba.platynereis.platybrowser.PlatyBrowser;
import de.embl.cba.platynereis.platybrowser.PlatyBrowserSourcesPanel;
import net.imagej.ImageJ;

public class OpenPlatyBrowser
{
	public static void main( String[] args )
	{
		new ImageJ().ui().showUI();

		final PlatyBrowser mainFrame = new PlatyBrowser(
				"0.2.1",
				"/Volumes/arendt/EM_6dpf_segmentation/EM-Prospr",
				"https://git.embl.de/tischer/platy-browser-tables/raw/master/" );

		final PlatyBrowserSourcesPanel sourcesPanel = mainFrame.getSourcesPanel();

//		sourcesPanel.addSourceToPanelAndViewer( "em-segmented-cells-labels-new-uint16" );

		sourcesPanel.addSourceToPanelAndViewer( "em-segmented-new-nuclei-uint16-labels" );

	}
}