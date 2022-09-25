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
package org.embl.mobie.viewer.color;

import bdv.viewer.SourceAndConverter;
import net.imglib2.converter.Converter;
import net.imglib2.display.ColorConverter;
import net.imglib2.type.numeric.ARGBType;
import org.embl.mobie.viewer.color.opacity.OpacityAdjuster;
import org.embl.mobie.viewer.display.AbstractDisplay;

import static net.imglib2.type.numeric.ARGBType.alpha;
import static net.imglib2.type.numeric.ARGBType.blue;
import static net.imglib2.type.numeric.ARGBType.green;
import static net.imglib2.type.numeric.ARGBType.red;

public abstract class OpacityHelper
{
	private static void adjustOpacity( SourceAndConverter< ? > sourceAndConverter, double opacity )
	{
		adjustOpacity( ( ColorConverter ) sourceAndConverter.getConverter(), opacity );

		if ( sourceAndConverter.asVolatile() != null )
		{
			adjustOpacity( ( ColorConverter ) sourceAndConverter.asVolatile().getConverter(), opacity );
		}
	}

	private static void adjustOpacity( ColorConverter colorConverter, double opacity  )
	{
		colorConverter.getColor().get();
		final int value = colorConverter.getColor().get();
		colorConverter.setColor( new ARGBType( ARGBType.rgba( red( value ), green( value ), blue( value ), alpha( value ) * opacity ) ) );
	}

	public static double getOpacity( SourceAndConverter< ? > sourceAndConverter )
	{
		final ColorConverter colorConverter = ( ColorConverter ) sourceAndConverter.getConverter();
		final int alpha = alpha( colorConverter.getColor().get() );
		return alpha / 255.0D;
	}

	public static void setOpacity( SourceAndConverter< ? > sourceAndConverter, double opacity )
	{
		final Converter< ?, ARGBType > converter = sourceAndConverter.getConverter();

		if ( converter instanceof OpacityAdjuster )
		{
			( ( OpacityAdjuster ) converter ).setOpacity( opacity );
		}
		else if ( converter instanceof ColorConverter )
		{
			adjustOpacity( sourceAndConverter, opacity );
		}
		else
		{
			throw new UnsupportedOperationException("Cannot adjust opacity of converter: " + converter.getClass().getName() );
		}
	}
}