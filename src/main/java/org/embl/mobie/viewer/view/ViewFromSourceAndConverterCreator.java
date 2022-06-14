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

import bdv.SpimSource;
import bdv.tools.brightness.ConverterSetup;
import bdv.tools.transformation.TransformedSource;
import bdv.util.ResampledSource;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import net.imglib2.converter.Converter;
import net.imglib2.display.ColorConverter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import org.embl.mobie.viewer.bdv.render.BlendingMode;
import org.embl.mobie.viewer.color.LabelConverter;
import org.embl.mobie.viewer.color.OpacityAdjuster;
import org.embl.mobie.viewer.display.ImageDisplay;
import org.embl.mobie.viewer.display.SourceDisplay;
import org.embl.mobie.viewer.source.LabelSource;
import org.embl.mobie.viewer.transform.AffineSourceTransformer;
import org.embl.mobie.viewer.source.MergedGridSource;
import org.embl.mobie.viewer.transform.SourceTransformer;
import sc.fiji.bdvpg.services.SourceAndConverterServices;

import java.util.ArrayList;
import java.util.List;

public class ViewFromSourceAndConverterCreator
{
	private final SourceAndConverter sourceAndConverter;

	// display settings
	private double opacity;
	private BlendingMode blendingMode;
	private double[] contrastLimits;
	private String color;

	// TODO: not used, what was this intended for?
	public ViewFromSourceAndConverterCreator( SourceAndConverter sourceAndConverter )
	{
		this.sourceAndConverter = sourceAndConverter;
	}

	public View getView()
	{
		final ArrayList< SourceDisplay > sourceDisplays = new ArrayList<>();
		final ArrayList< SourceTransformer > sourceTransformers = new ArrayList<>();
		final View view = new View( "uiSelectionGroup", sourceDisplays, sourceTransformers, false );

		// recursively add all transformations
		// FIXME: in fact this will be the wrong order.
		addSourceTransformers( sourceAndConverter.getSpimSource(), sourceTransformers );

		if ( sourceAndConverter.getConverter() instanceof LabelConverter )
		{
			throwError( sourceAndConverter.getConverter() );
		}
		else
		{
			sourceDisplays.add( new ImageDisplay( sourceAndConverter ) );
		}

		return view;
	}

	private void createSourceDisplay( SourceAndConverter< ? > sourceAndConverter  )
	{
		final Converter< ?, ARGBType > converter = sourceAndConverter.getConverter();
		final ConverterSetup converterSetup = SourceAndConverterServices.getSourceAndConverterService().getConverterSetup( sourceAndConverter );

		if( converter instanceof OpacityAdjuster )
			opacity = ( ( OpacityAdjuster ) converter ).getOpacity();

		if ( converter instanceof ColorConverter )
		{
			// needs to be of form r=(\\d+),g=(\\d+),b=(\\d+),a=(\\d+)"
			color = ( ( ColorConverter ) converter ).getColor().toString();
			color = color.replaceAll("[()]", "");
		}

		contrastLimits = new double[2];
		contrastLimits[0] = converterSetup.getDisplayRangeMin();
		contrastLimits[1] = converterSetup.getDisplayRangeMax();

		blendingMode = (BlendingMode) SourceAndConverterServices.getSourceAndConverterService().getMetadata( sourceAndConverter, BlendingMode.BLENDING_MODE );
	}

	private void addSourceTransformers( Source< ? > source, List< SourceTransformer > sourceTransformers )
	{
		if ( source instanceof SpimSource )
		{
			return;
		}
		else if ( source instanceof TransformedSource )
		{
			final TransformedSource transformedSource = ( TransformedSource ) source;

			AffineTransform3D fixedTransform = new AffineTransform3D();
			transformedSource.getFixedTransform( fixedTransform );
			if ( ! fixedTransform.isIdentity() )
			{
				sourceTransformers.add( new AffineSourceTransformer( transformedSource ) );
			}

			final Source< ? > wrappedSource = transformedSource.getWrappedSource();

			addSourceTransformers( wrappedSource, sourceTransformers );
		}
		else if (  source instanceof LabelSource )
		{
			final Source< ? > wrappedSource = (( LabelSource ) source).getWrappedSource();

			addSourceTransformers( wrappedSource, sourceTransformers );
		}
		else if (  source instanceof MergedGridSource )
		{
			throwError( source );
		}
		else if (  source instanceof ResampledSource )
		{
			throwError( source );
		}
		else
		{
			throwError( source );
		}
	}

	private void throwError( Object object )
	{
		throw new UnsupportedOperationException( "Cannot yet create a view from a " + object.getClass().getName() );
	}

}
