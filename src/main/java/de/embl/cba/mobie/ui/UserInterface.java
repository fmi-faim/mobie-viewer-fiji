package de.embl.cba.mobie.ui;

import bdv.tools.brightness.ConverterSetup;
import bdv.tools.brightness.SliderPanelDouble;
import bdv.util.BdvHandle;
import bdv.util.BoundedValueDouble;
import bdv.viewer.Interpolation;
import de.embl.cba.bdv.utils.BdvUtils;
import de.embl.cba.bdv.utils.BrightnessUpdateListener;
import de.embl.cba.mobie.image.SourceAndMetadataChangedListener;
import de.embl.cba.tables.image.SourceAndMetadata;
import ij.WindowManager;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.List;

public class UserInterface implements SourceAndMetadataChangedListener
{
	private final UserInterfaceComponentsProvider componentsProvider;
	private final JPanel displaySettingsPanel;
	private final SourcesDisplayManager displayManager;
	private HashMap< Object, JPanel > sourceToPanel;
	private final JFrame frame;
	private final JPanel actionPanel;

	public UserInterface( MoBIE moBIE )
	{
		displayManager = moBIE.getSourcesDisplayManager();
		displayManager.listeners().add( this );

		componentsProvider = new UserInterfaceComponentsProvider( moBIE );

		actionPanel = createActionsPanel( moBIE );
		displaySettingsPanel = createDisplaySettingsPanel();

		frame = createAndShowFrame( moBIE, actionPanel, displaySettingsPanel );
		setImageJLogWindowPositionAndSize( frame );
	}

	public void dispose()
	{
		frame.dispose();
	}

	private JPanel createDisplaySettingsPanel()
	{
		final JPanel panel = new JPanel();
		panel.setLayout( new BoxLayout(panel, BoxLayout.Y_AXIS ) );
		panel.setAlignmentX( Component.LEFT_ALIGNMENT );

		sourceToPanel = new HashMap<>();

		return panel;
	}

	private JPanel createActionsPanel( MoBIE moBIE )
	{
		final JPanel actionPanel = new JPanel();
		actionPanel.setLayout( new BoxLayout( actionPanel, BoxLayout.Y_AXIS ) );

		actionPanel.add( componentsProvider.createInfoPanel( moBIE.getProjectLocation(), moBIE.getOptions().values.getPublicationURL() ) );
		actionPanel.add( new JSeparator( SwingConstants.HORIZONTAL ) );

		actionPanel.add( componentsProvider.createDatasetSelectionPanel() );
		actionPanel.add( new JSeparator( SwingConstants.HORIZONTAL ) );

		actionPanel.add( componentsProvider.createSourceSelectionPanel( moBIE.getSourcesDisplayManager() ) );
		actionPanel.add( new JSeparator( SwingConstants.HORIZONTAL ) );

		actionPanel.add( componentsProvider.createBookmarksPanel( moBIE.getBookmarkManager() )  );
		actionPanel.add( componentsProvider.createMoveToLocationPanel( )  );

		if ( moBIE.getLevelingVector() != null )
		{
			actionPanel.add( componentsProvider.createLevelingPanel( moBIE.getLevelingVector() ) );
		}
		return actionPanel;
	}

	private JFrame createAndShowFrame( MoBIE moBIE, JPanel actionPanel, JPanel displaySettingsPanel )
	{
		JFrame frame = new JFrame( "MoBIE: " + moBIE.getProjectName() + "-" + moBIE.getDataset() );

		JSplitPane splitPane = new JSplitPane();
		splitPane.setOrientation( JSplitPane.VERTICAL_SPLIT );
		final int sourceSelectionPanelHeight = componentsProvider.getSourceSelectionPanelHeight();
		final int actionPanelHeight = sourceSelectionPanelHeight + 7 * 40;

		splitPane.setDividerLocation( actionPanelHeight );
		splitPane.setTopComponent( actionPanel );
		splitPane.setBottomComponent( displaySettingsPanel );
		splitPane.setAutoscrolls( true );

		// show frame
		frame.setPreferredSize( new Dimension( 600, actionPanelHeight + 200 ) );
		frame.getContentPane().setLayout( new GridLayout() );
		frame.getContentPane().add( splitPane );

		frame.setDefaultCloseOperation( JFrame.DISPOSE_ON_CLOSE );
		frame.pack();
		frame.setVisible( true );

		return frame;
	}

	private void setImageJLogWindowPositionAndSize( JFrame parentComponent )
	{
		final Frame log = WindowManager.getFrame( "Log" );
		if (log != null) {
			Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
			final int logWindowHeight = screenSize.height - ( parentComponent.getLocationOnScreen().y + parentComponent.getHeight() + 20 );
			log.setSize( parentComponent.getWidth(), logWindowHeight  );
			log.setLocation( parentComponent.getLocationOnScreen().x, parentComponent.getLocationOnScreen().y + parentComponent.getHeight() );
		}
	}

	public void setBdvWindowPositionAndSize( BdvHandle bdvHandle )
	{
		BdvUtils.getViewerFrame( bdvHandle ).setLocation(
				frame.getLocationOnScreen().x + frame.getWidth(),
				frame.getLocationOnScreen().y );

		BdvUtils.getViewerFrame( bdvHandle ).setSize( frame.getHeight(), frame.getHeight() );

		bdvHandle.getViewerPanel().setInterpolation( Interpolation.NLINEAR );
	}

	private void refresh()
	{
		displaySettingsPanel.revalidate();
		displaySettingsPanel.repaint();
		frame.revalidate();
		frame.repaint();
	}

	@Override
	public synchronized void addedToBDV( SourceAndMetadata< ? > sam )
	{
		Object panelKey = getPanelKey( sam );

		if ( sourceToPanel.containsKey( panelKey ) )
		{
			return;
		}
		else
		{
			final JPanel panel = componentsProvider.createDisplaySettingsPanel( sam, displayManager );
			displaySettingsPanel.add( panel );
			sourceToPanel.put( panelKey, panel );
			refresh();
		}
	}

	protected Object getPanelKey( SourceAndMetadata< ? > sam )
	{
		Object panelKey;
		if ( sam.metadata().groupId != null )
			panelKey = sam.metadata().groupId;
		else
			panelKey = sam.metadata();
		return panelKey;
	}

	@Override
	public void removedFromBDV( SourceAndMetadata< ? > sam )
	{
		final JPanel panel = sourceToPanel.get( getPanelKey( sam ) );
		displaySettingsPanel.remove( panel );
		sourceToPanel.remove( sam );
		refresh();
	}

	public static void showBrightnessDialog(
			String name,
			List< ConverterSetup > converterSetups,
			double rangeMin,
			double rangeMax )
	{
		JFrame frame = new JFrame( name );
		frame.setDefaultCloseOperation( JFrame.DISPOSE_ON_CLOSE );

		final double currentRangeMin = converterSetups.get( 0 ).getDisplayRangeMin();
		final double currentRangeMax = converterSetups.get( 0 ).getDisplayRangeMax();

		final BoundedValueDouble min =
				new BoundedValueDouble(
						rangeMin,
						rangeMax,
						currentRangeMin );

		final BoundedValueDouble max =
				new BoundedValueDouble(
						rangeMin,
						rangeMax,
						currentRangeMax );

		double spinnerStepSize = ( currentRangeMax - currentRangeMin ) / 100.0;

		JPanel panel = new JPanel();
		panel.setLayout( new BoxLayout( panel, BoxLayout.PAGE_AXIS ) );
		final SliderPanelDouble minSlider =
				new SliderPanelDouble( "Min", min, spinnerStepSize );
		minSlider.setNumColummns( 7 );
		minSlider.setDecimalFormat( "####E0" );

		final SliderPanelDouble maxSlider =
				new SliderPanelDouble( "Max", max, spinnerStepSize );
		maxSlider.setNumColummns( 7 );
		maxSlider.setDecimalFormat( "####E0" );

		final BrightnessUpdateListener brightnessUpdateListener =
				new BrightnessUpdateListener(
						min, max, minSlider, maxSlider, converterSetups );

		min.setUpdateListener( brightnessUpdateListener );
		max.setUpdateListener( brightnessUpdateListener );

		panel.add( minSlider );
		panel.add( maxSlider );

		frame.setContentPane( panel );

		//Display the window.
		frame.setBounds( MouseInfo.getPointerInfo().getLocation().x,
				MouseInfo.getPointerInfo().getLocation().y,
				120, 10);
		frame.setResizable( false );
		frame.pack();
		frame.setVisible( true );

	}
}