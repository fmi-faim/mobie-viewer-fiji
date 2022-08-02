/*-
 * #%L
 * Fiji viewer for MoBIE projects
 * %%
 * Copyright (C) 2018 - 2022 EMBL
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package org.embl.mobie.viewer.view;

import bdv.tools.transformation.TransformedSource;
import bdv.util.BdvHandle;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import ij.IJ;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import org.apache.commons.lang.ArrayUtils;
import org.embl.mobie.viewer.ImageStore;
import org.embl.mobie.viewer.MoBIE;
import org.embl.mobie.viewer.annotation.SegmentAnnotation;
import org.embl.mobie.viewer.annotation.Annotation;
import org.embl.mobie.viewer.bdv.view.AnnotationSliceView;
import org.embl.mobie.viewer.bdv.view.ImageSliceView;
import org.embl.mobie.viewer.bdv.view.SliceViewer;
import org.embl.mobie.viewer.color.CategoricalAnnotationColoringModel;
import org.embl.mobie.viewer.color.ColorHelper;
import org.embl.mobie.viewer.color.ColoringModels;
import org.embl.mobie.viewer.color.MobieColoringModel;
import org.embl.mobie.viewer.color.NumericAnnotationColoringModel;
import org.embl.mobie.viewer.color.lut.ColumnARGBLut;
import org.embl.mobie.viewer.color.lut.LUTs;
import org.embl.mobie.viewer.display.AbstractDisplay;
import org.embl.mobie.viewer.display.AnnotationDisplay;
import org.embl.mobie.viewer.display.Display;
import org.embl.mobie.viewer.display.ImageDisplay;
import org.embl.mobie.viewer.display.ImageAnnotationDisplay;
import org.embl.mobie.viewer.display.SegmentationDisplay;
import org.embl.mobie.viewer.plot.ScatterPlotView;
import org.embl.mobie.viewer.select.MoBIESelectionModel;
import org.embl.mobie.viewer.serialize.ImageSource;
import org.embl.mobie.viewer.source.AnnotatedImage;
import org.embl.mobie.viewer.source.BoundarySource;
import org.embl.mobie.viewer.source.Image;
import org.embl.mobie.viewer.source.AnnotatedLabelImage;
import org.embl.mobie.viewer.source.RegionLabelImage;
import org.embl.mobie.viewer.source.StorageLocation;
import org.embl.mobie.viewer.source.TransformedImage;
import org.embl.mobie.viewer.table.AnnData;
import org.embl.mobie.viewer.table.AnnotationTableModel;
import org.embl.mobie.viewer.table.DefaultAnnData;
import org.embl.mobie.viewer.table.TableDataFormat;
import org.embl.mobie.viewer.table.TableView;
import org.embl.mobie.viewer.table.saw.TableSawAnnotationCreator;
import org.embl.mobie.viewer.table.saw.TableSawAnnotationTableModel;
import org.embl.mobie.viewer.table.saw.TableSawImageAnnotation;
import org.embl.mobie.viewer.table.saw.TableSawImageAnnotationCreator;
import org.embl.mobie.viewer.transform.AnnotatedSegmentTransformer;
import org.embl.mobie.viewer.transform.NormalizedAffineViewerTransform;
import org.embl.mobie.viewer.transform.SliceViewLocationChanger;
import org.embl.mobie.viewer.transform.TransformHelper;
import org.embl.mobie.viewer.transform.image.ImageTransformation;
import org.embl.mobie.viewer.transform.image.MergedGridTransformation;
import org.embl.mobie.viewer.transform.image.Transformation;
import org.embl.mobie.viewer.transform.TransformedAnnData;
import org.embl.mobie.viewer.transform.image.AffineTransformation;
import org.embl.mobie.viewer.ui.UserInterface;
import org.embl.mobie.viewer.ui.WindowArrangementHelper;
import org.embl.mobie.viewer.view.save.ViewSaver;
import org.embl.mobie.viewer.volume.ImageVolumeViewer;
import org.embl.mobie.viewer.volume.SegmentsVolumeViewer;
import org.embl.mobie.viewer.volume.UniverseManager;
import net.imglib2.realtransform.AffineTransform3D;
import sc.fiji.bdvpg.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.services.SourceAndConverterServices;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ViewManager
{
	static { net.imagej.patcher.LegacyInjector.preinit(); }

	private final MoBIE moBIE;
	private final UserInterface userInterface;
	private final SliceViewer sliceViewer;
	private final SourceAndConverterService sacService;
	private List< Display > currentDisplays;
	private List< Transformation > currentTransformers;
	private final UniverseManager universeManager;
	private final AdditionalViewsLoader additionalViewsLoader;
	private final ViewSaver viewSaver;
	private int numViewedTables = 0;

	public ViewManager( MoBIE moBIE, UserInterface userInterface, boolean is2D )
	{
		this.moBIE = moBIE;
		this.userInterface = userInterface;
		currentDisplays = new ArrayList<>();
		currentTransformers = new ArrayList<>();
		sliceViewer = new SliceViewer( moBIE, is2D );
		universeManager = new UniverseManager();
		additionalViewsLoader = new AdditionalViewsLoader( moBIE );
		viewSaver = new ViewSaver( moBIE );
		sacService = ( SourceAndConverterService ) SourceAndConverterServices.getSourceAndConverterService();
	}

	private void initScatterPlotView( AnnotationDisplay< ? > display )
	{
		// TODO: implement for multiple images
		//   probably needs an AnnotationTableModel constructed
		//   from multiple tables
		//   Note that the same code is needed for the TableView,
		//   thus maybe this needs to happen within annotationDisplay?
		final AnnotatedImage annotatedImage = ( AnnotatedImage ) display.getImages().iterator().next();
		final AnnotationTableModel annotationTableModel = annotatedImage.getAnnData().getTable();

		String[] scatterPlotAxes = display.getScatterPlotAxes();
		display.scatterPlotView = new ScatterPlotView( annotationTableModel, display.selectionModel, display.coloringModel, scatterPlotAxes, new double[]{1.0, 1.0}, 0.5 );
		display.selectionModel.listeners().add( display.scatterPlotView );
		display.coloringModel.listeners().add( display.scatterPlotView );
		display.sliceViewer.getBdvHandle().getViewerPanel().addTimePointListener( display.scatterPlotView );

		if ( display.showScatterPlot() )
		{
			display.scatterPlotView.setShowConfigurationUI( false );
			display.scatterPlotView.show();
		}
	}

	public List< Display > getCurrentSourceDisplays()
	{
		return currentDisplays;
	}

	public SliceViewer getSliceViewer()
	{
		return sliceViewer;
	}

	public AdditionalViewsLoader getAdditionalViewsLoader() { return additionalViewsLoader; }

	public ViewSaver getViewsSaver() { return viewSaver; }

	private void addManualTransforms( List< Transformation > viewSourceTransforms, Map< String, SourceAndConverter< ? > > sourceNameToSourceAndConverter )
	{
        for ( String sourceName: sourceNameToSourceAndConverter.keySet() ) {
            Source< ? > source = sourceNameToSourceAndConverter.get( sourceName ).getSpimSource();

            if ( source instanceof BoundarySource ) {
                source = (( BoundarySource ) source).getWrappedSource();
            }
            TransformedSource transformedSource = (TransformedSource) source;

            AffineTransform3D fixedTransform = new AffineTransform3D();
            transformedSource.getFixedTransform( fixedTransform );
            if ( !fixedTransform.isIdentity() ) {
                List<String> sources = new ArrayList<>();
                sources.add( sourceName );
                viewSourceTransforms.add( new AffineTransformation( "manualTransform", fixedTransform.getRowPackedCopy(), sources ) );
            }
        }
    }

	public View createViewFromCurrentState( String uiSelectionGroup, boolean isExclusive, boolean includeViewerTransform )
	{
		List< Display > viewDisplays = new ArrayList<>();
		List< Transformation > viewSourceTransforms = new ArrayList<>();

		for ( Transformation imageTransformation : currentTransformers )
			if ( ! viewSourceTransforms.contains( imageTransformation ) )
				viewSourceTransforms.add( imageTransformation );

		for ( Display display : currentDisplays )
		{
			Display currentDisplay = null;

			if ( display instanceof ImageDisplay )
			{
				ImageDisplay imageDisplay = ( ImageDisplay ) display;
				currentDisplay = new ImageDisplay( imageDisplay );
				addManualTransforms( viewSourceTransforms, imageDisplay.nameToSourceAndConverter );
			}
			else if ( display instanceof SegmentationDisplay )
			{
				SegmentationDisplay segmentationDisplay = ( SegmentationDisplay ) display;
				currentDisplay = new SegmentationDisplay( segmentationDisplay );
				addManualTransforms( viewSourceTransforms, ( Map ) segmentationDisplay.nameToSourceAndConverter );
			}
			else if ( display instanceof ImageAnnotationDisplay )
			{
				currentDisplay = new ImageAnnotationDisplay( ( ImageAnnotationDisplay ) display );
			}

			if ( currentDisplay != null )
			{
				viewDisplays.add( currentDisplay );
			}
		}

		if ( includeViewerTransform )
		{
			final BdvHandle bdvHandle = sliceViewer.getBdvHandle();
			AffineTransform3D normalisedViewTransform = TransformHelper.createNormalisedViewerTransform( bdvHandle.getViewerPanel() );
			final NormalizedAffineViewerTransform transform = new NormalizedAffineViewerTransform( normalisedViewTransform.getRowPackedCopy(), bdvHandle.getViewerPanel().state().getCurrentTimepoint() );
			return new View(uiSelectionGroup, viewDisplays, viewSourceTransforms, transform, isExclusive);
		}
		else
		{
			return new View(uiSelectionGroup, viewDisplays, viewSourceTransforms, isExclusive);
		}
	}

	public synchronized void show( String view )
	{
		show( moBIE.getViews().get( view ) );
	}

	public synchronized void show( View view )
	{
		if ( view.isExclusive() )
		{
			removeAllSourceDisplays( true );
		}

		if ( view.getViewerTransform() != null )
		{
			SliceViewLocationChanger.changeLocation( sliceViewer.getBdvHandle(), view.getViewerTransform() );
		}

		// init and transform
		initImages( view );

		// display the data
		final List< Display > displays = view.getSourceDisplays();
		for ( Display display : displays )
			show( display );

		// adjust viewer transform
		if ( view.getViewerTransform() == null && currentDisplays.size() > 0 && ( view.isExclusive() || currentDisplays.size() == 1 ) )
		{
			final Display display = currentDisplays.get( currentDisplays.size() - 1);
			new ViewerTransformAdjuster( sliceViewer.getBdvHandle(), (( AbstractDisplay< ? > ) display ).nameToSourceAndConverter.values().iterator().next() ).run();
		}

		// trigger rendering of source name overlay
		getSliceViewer().getSourceNameRenderer().transformChanged( sliceViewer.getBdvHandle().getViewerPanel().state().getViewerTransform() );

		// adapt time point
		if ( view.getViewerTransform() != null )
		{
			// This needs to be done after adding all the sources,
			// because otherwise the requested timepoint may not yet
			// exist in BDV
			SliceViewLocationChanger.adaptTimepoint( sliceViewer.getBdvHandle(), view.getViewerTransform() );
		}
	}

	// initialize and transform
	public void initImages( View view )
	{
		// fetch names of all sources that are
		// either to be shown or to be transformed
		final Map< String, Object > imageToDisplay = view.getImages();
		if ( imageToDisplay.size() == 0 ) return;

		// instantiate images that can be directly opened
		// some others may be created later, by a display or transformation
		final List< ImageSource > imageSources = moBIE.getImageSources( imageToDisplay.keySet() );

		for ( ImageSource imageSource : imageSources )
		{
			if ( imageToDisplay.get( imageSource.getName() ) instanceof MergedGridTransformation )
				imageSource.openLazy( true );
			else
				imageSource.openLazy( false );
		}

		moBIE.initImages( imageSources );

		final Map< String, Image< ? > > images = ImageStore.images;

		// transform images
		// this may create new images with new names
		final List< Transformation > transformations = view.getTransformations();
		if ( transformations != null )
		{
			for ( Transformation transformation : transformations )
			{
				currentTransformers.add( transformation );

				if ( transformation instanceof ImageTransformation )
				{
					final ImageTransformation imageTransformation = ( ImageTransformation ) transformation;
					for ( String name : images.keySet() )
					{
						if ( imageTransformation.getTargetImageNames().contains( name ) )
						{
							final Image image = images.get( name );
							if ( AnnotatedLabelImage.class.isAssignableFrom( image.getClass() ) )
							{
								final AnnotatedLabelImage transformedImage = transform( imageTransformation, ( AnnotatedLabelImage ) image );
								images.put( transformedImage.getName(), transformedImage );
							}
							else
							{
								final TransformedImage transformedImage = new TransformedImage( image, imageTransformation );
								images.put( transformedImage.getName(), transformedImage );
							}
						}
					}
				}
				else if ( transformation instanceof MergedGridTransformation )
				{
					final List< String > targetImageNames = transformation.getTargetImageNames();
					final List targetImages = ImageStore.getImages( targetImageNames );
					final Image transformed = ( ( MergedGridTransformation< ? > ) transformation ).apply( targetImages );
					ImageStore.images.put( transformed.getName(), transformed );
				}
			}
		}

		// instantiate images that are created by a display
		//
		for ( Display< ? > sourceDisplay : view.getSourceDisplays() )
		{
			// https://github.com/mobie/mobie-viewer-fiji/issues/818
			if ( sourceDisplay instanceof ImageAnnotationDisplay )
			{
				final ImageAnnotationDisplay< ? > imageAnnotationDisplay = ( ImageAnnotationDisplay< ? > ) sourceDisplay;
				final Map< TableDataFormat, StorageLocation > tableData = imageAnnotationDisplay.tableData;
				// note that the imageNames that are referred to here must exist in this view
				// thus we do this *after* the above transformations, which may create new
				// images that could be referred to.
				final Map< String, List< String > > regionIdToImageNames = imageAnnotationDisplay.sources;
				final Set< String > columnPaths = moBIE.getTablePaths( tableData );
				final String defaultColumnsPath = columnPaths.stream().filter( p -> p.contains( "default" ) ).findAny().get();
				final TableSawAnnotationCreator< TableSawImageAnnotation > annotationCreator = new TableSawImageAnnotationCreator( regionIdToImageNames );
				final TableSawAnnotationTableModel tableModel = new TableSawAnnotationTableModel( annotationCreator, defaultColumnsPath );
				final Set annotations = tableModel.annotations();
				final Image< UnsignedIntType > labelImage = new RegionLabelImage( imageAnnotationDisplay.getName(), annotations );
				final DefaultAnnData< TableSawImageAnnotation > annData = new DefaultAnnData<>( tableModel );
				final AnnotatedLabelImage annotatedLabelImage = new AnnotatedLabelImage( labelImage, annData );

				// label image representing
				// image annotations
				ImageStore.images.put( annotatedLabelImage.getName(), annotatedLabelImage );
			}
		}

	}

	private AnnotatedLabelImage< ? extends SegmentAnnotation > transform( ImageTransformation transformation, AnnotatedLabelImage< ? extends SegmentAnnotation > annotatedLabelImage )
	{
		final TransformedImage transformedLabelImage = new TransformedImage( annotatedLabelImage.getLabelImage(), transformation );
		final AnnData< ? extends SegmentAnnotation > annData = annotatedLabelImage.getAnnData();
		final AnnotatedSegmentTransformer segmentTransformer = new AnnotatedSegmentTransformer( transformation );
		final TransformedAnnData transformedAnnData = new TransformedAnnData( annData, segmentTransformer );

		final AnnotatedLabelImage< ? extends SegmentAnnotation > transformedAnnotatedLabelImage = new AnnotatedLabelImage( transformedLabelImage, transformedAnnData );

		return transformedAnnotatedLabelImage;
	}

	public synchronized < A extends Annotation > void show( Display< ? > display )
	{
		if ( currentDisplays.contains( display ) ) return;

		if ( display instanceof ImageDisplay )
		{
			for ( String name : display.getSources() )
				display.addImage( ( Image ) ImageStore.images.get( name ) );
			showImageDisplay( ( ImageDisplay ) display );
		}
		else if ( display instanceof AnnotationDisplay )
		{
			// TODO:
			//   What about label mask images that are NOT annotated,
			//   i.e. don't have a table?
			//   Maybe implement an AnnData that lazily builds up
			//   upon browsing around?
			//   This may be tricky, because if we want to support semantic
			//   segmentations there is no anchor(), maybe still fine, but
			//   then the table would not move the slice view anywhere.

			final AnnotationDisplay< A > annotationDisplay = ( AnnotationDisplay ) display;

			// add all images that are shown by this display
			for ( String name : display.getSources() )
				annotationDisplay.addImage( ( AnnotatedImage ) ImageStore.images.get( name ) );

			// now that all images are added create an
			// annData object for the display,
			// potentially combining the annData from
			// all images that are displayed.
			annotationDisplay.createAnnData();

			// configure selection model
			//
			annotationDisplay.selectionModel = new MoBIESelectionModel<>();

			// set selected segments
			//
			if ( annotationDisplay.selectedAnnotationIds() != null )
			{
				final Set< A > annotations = annotationDisplay.annotationAdapter.getAnnotations( annotationDisplay.selectedAnnotationIds() );
				annotationDisplay.selectionModel.setSelected( annotations, true );
			}

			// configure coloring model
			//
			String lut = annotationDisplay.getLut();

			if ( LUTs.isCategorical( lut ) )
			{
				CategoricalAnnotationColoringModel< Annotation > coloringModel =
						ColoringModels.createCategoricalModel(
								annotationDisplay.getColoringColumnName(),
								lut,
								LUTs.TRANSPARENT );

				if ( LUTs.getLut( lut ) instanceof ColumnARGBLut )
				{
					// note that this currently forces loading of the table(s)
					// for big data this may need some improvement
					final AnnotationTableModel< A > table = annotationDisplay.getAnnData().getTable();
					for ( A annotation : table.annotations() )
					{
						String argbString = annotation.getValue( annotationDisplay.getColoringColumnName() ).toString();

						if ( argbString.equals("") )
							continue;

						final ARGBType argbType = ColorHelper.getArgbType( argbString );

						coloringModel.assignColor( argbString, argbType.get() );
					}
				}

				coloringModel.setRandomSeed( annotationDisplay.getRandomColorSeed() );
				annotationDisplay.coloringModel = new MobieColoringModel( coloringModel, annotationDisplay.selectionModel );
			}

	else if ( LUTs.isNumeric( lut ) )
			{
				NumericAnnotationColoringModel< Annotation > coloringModel
						= ColoringModels.createNumericModel(
								annotationDisplay.getColoringColumnName(),
								lut,
								annotationDisplay.getValueLimits(),
						 true
							);

				annotationDisplay.coloringModel = new MobieColoringModel( coloringModel, annotationDisplay.selectionModel );
			}
			else
			{
				throw new UnsupportedOperationException("Coloring LUT " + lut + " is not supported.");
			}

			// show in slice viewer
			annotationDisplay.sliceViewer = sliceViewer;
			annotationDisplay.sliceView = new AnnotationSliceView<>( moBIE, annotationDisplay );
			initTableView( annotationDisplay );
			initScatterPlotView( annotationDisplay );
			if ( annotationDisplay instanceof SegmentationDisplay )
				initSegmentationVolumeViewer( ( SegmentationDisplay ) annotationDisplay );
		}

		userInterface.addSourceDisplay( display );
		currentDisplays.add( display );
	}

	public synchronized void removeAllSourceDisplays( boolean closeImgLoader )
	{
		// create a copy of the currently shown displays...
		final ArrayList< Display > currentDisplays = new ArrayList<>( this.currentDisplays ) ;

		// ...such that we can remove the displays without
		// modifying the list that we iterate over
		for ( Display display : currentDisplays )
		{
			// removes display from all viewers and
			// also from the list of currently shown sourceDisplays
			// also close all ImgLoaders to free the cache
			removeDisplay( display, closeImgLoader );
		}
	}

	private void showImageDisplay( ImageDisplay imageDisplay )
	{
		imageDisplay.sliceViewer = sliceViewer;
		imageDisplay.imageSliceView = new ImageSliceView( moBIE, imageDisplay );
		initImageVolumeViewer( imageDisplay );
	}

	// compare with initSegmentationVolumeViewer
	private void initImageVolumeViewer( ImageDisplay< ? > imageDisplay )
	{
		imageDisplay.imageVolumeViewer = new ImageVolumeViewer( imageDisplay.nameToSourceAndConverter, universeManager );
		Double[] resolution3dView = imageDisplay.getResolution3dView();
		if ( resolution3dView != null ) {
			imageDisplay.imageVolumeViewer.setVoxelSpacing( ArrayUtils.toPrimitive(imageDisplay.getResolution3dView() ));
		}
		imageDisplay.imageVolumeViewer.showImages( imageDisplay.showImagesIn3d() );

		for ( SourceAndConverter< ? > sourceAndConverter : imageDisplay.nameToSourceAndConverter.values() )
		{
			sacService.setMetadata( sourceAndConverter, ImageVolumeViewer.class.getName(), imageDisplay.imageVolumeViewer );
		}
	}

	private void initTableView( AnnotationDisplay< ? extends Annotation > display )
	{
		display.tableView = new TableView( display );
		display.tableView.show();
		setTablePosition( display.sliceViewer.getWindow(), display.tableView.getWindow() );
		display.selectionModel.listeners().add( display.tableView );
		display.coloringModel.listeners().add( display.tableView );
		numViewedTables++;
	}

	private void setTablePosition( Window reference, Window table )
	{
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		final int shift = screenSize.height / 20;
		SwingUtilities.invokeLater( () -> WindowArrangementHelper.bottomAlignWindow( reference, table, ( numViewedTables - 1 ) * shift ) );
	}

	private void initSegmentationVolumeViewer( SegmentationDisplay display )
	{
		display.segmentsVolumeViewer = new SegmentsVolumeViewer( display.selectionModel, display.coloringModel, display.nameToSourceAndConverter.values(), universeManager );
		Double[] resolution3dView = display.getResolution3dView();
		if ( resolution3dView != null ) {
			display.segmentsVolumeViewer.setVoxelSpacing( ArrayUtils.toPrimitive(display.getResolution3dView()) );
		}
		display.segmentsVolumeViewer.showSegments( display.showSelectedSegmentsIn3d(), true );
		display.coloringModel.listeners().add( display.segmentsVolumeViewer );
		display.selectionModel.listeners().add( display.segmentsVolumeViewer );
	}

	public synchronized void removeDisplay( Display display, boolean closeImgLoader )
	{
		if ( display instanceof AnnotationDisplay )
		{
			final AnnotationDisplay< ? > annotationDisplay = ( AnnotationDisplay< ? > ) display;
			annotationDisplay.sliceView.close( closeImgLoader );

			if ( annotationDisplay.tableView != null )
			{
				numViewedTables--;
				annotationDisplay.tableView.close();
				annotationDisplay.scatterPlotView.close();
				if ( annotationDisplay instanceof SegmentationDisplay )
					( ( SegmentationDisplay ) annotationDisplay ).segmentsVolumeViewer.close();
			}

		}
		else if ( display instanceof ImageDisplay )
		{
			final ImageDisplay imageDisplay = ( ImageDisplay ) display;
			imageDisplay.imageSliceView.close( false );
		}

		userInterface.removeDisplaySettingsPanel( display );
		currentDisplays.remove( display );

		updateCurrentSourceTransformers();
	}

	private void updateCurrentSourceTransformers()
	{
		// remove any sourceTransformers, where none of its relevant sources are displayed

		// create a copy of the currently shown source transformers, so we don't iterate over a list that we modify
		final ArrayList< Transformation > imageTransformersCopy = new ArrayList<>( this.currentTransformers ) ;

		Set<String> currentlyDisplayedSources = new HashSet<>();
		for ( Display display: currentDisplays )
			currentlyDisplayedSources.addAll( display.getSources() );

		for ( Transformation imageTransformation : imageTransformersCopy )
		{
			if ( ! currentlyDisplayedSources.stream().anyMatch( s -> imageTransformation.getTargetImageNames().contains( s ) ) )
				currentTransformers.remove( imageTransformation );
		}
	}

	// TODO: typing ( or remove )
	public Collection< AnnotationDisplay< ? > > getAnnotationDisplays()
	{
		final List< AnnotationDisplay< ? > > collect = getCurrentSourceDisplays().stream().filter( s -> s instanceof AnnotationDisplay ).map( s -> ( AnnotationDisplay< ? > ) s ).collect( Collectors.toList() );

		return collect;
	}

	public void close()
	{
		IJ.log( "Closing BDV..." );
		removeAllSourceDisplays( true );
		sliceViewer.getBdvHandle().close();
		IJ.log( "Closing 3D Viewer..." );
		universeManager.close();
		IJ.log( "Closing UI..." );
		userInterface.close();
	}
}
