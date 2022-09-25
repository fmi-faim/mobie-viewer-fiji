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
package org.embl.mobie.viewer.image;

import bdv.viewer.Source;
import de.embl.cba.tables.Outlier;
import de.embl.cba.tables.Utils;
import net.imglib2.Interval;
import net.imglib2.KDTree;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.Sampler;
import net.imglib2.Volatile;
import net.imglib2.neighborsearch.NearestNeighborSearchOnKDTree;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;
import net.imglib2.position.FunctionRealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.roi.RealMaskRealInterval;
import net.imglib2.roi.geom.GeomMasks;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.util.Intervals;
import org.embl.mobie.viewer.annotation.AnnotatedSpot;
import org.embl.mobie.viewer.source.RealRandomAccessibleIntervalTimelapseSource;
import org.embl.mobie.viewer.source.SourcePair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SpotLabelImage< AS extends AnnotatedSpot > implements Image< UnsignedIntType >
{
	private final String name;
	private final Set< AS > annotatedSpots;
	private Source< UnsignedIntType > source;
	private Source< ? extends Volatile< UnsignedIntType > > volatileSource = null;
	private KDTree< AS > kdTree;
	private RealMaskRealInterval mask;

	public SpotLabelImage( String name, Set< AS > annotatedSpots )
	{
		this.name = name;
		this.annotatedSpots = annotatedSpots;
		createLabelImage();
	}

	private void createLabelImage()
	{
		kdTree = new KDTree( new ArrayList<>( annotatedSpots ), new ArrayList<>( annotatedSpots ) );
		mask = GeomMasks.closedBox( kdTree.minAsDoubleArray(), kdTree.maxAsDoubleArray() );

		// TODO: use those for something?
		final NearestNeighborSearchOnKDTree< AS > nearestNeighborSearchOnKDTree = new NearestNeighborSearchOnKDTree<>( kdTree );
		final RadiusNeighborSearchOnKDTree< AS > radiusNeighborSearchOnKDTree = new RadiusNeighborSearchOnKDTree<>( kdTree );

		// TODO: code duplication with RegionLabelImage
		final ArrayList< Integer > timePoints = configureTimePoints();
		final Interval interval = Intervals.smallestContainingInterval( getMask() );


		final FunctionRealRandomAccessible< UnsignedIntType > realRandomAccessible = new FunctionRealRandomAccessible( 3, new LocationToLabelSupplier(), UnsignedIntType::new );
		source = new RealRandomAccessibleIntervalTimelapseSource<>( realRandomAccessible, interval, new UnsignedIntType(), new AffineTransform3D(), name, true, timePoints );

	}

	class LocationToLabelSupplier implements Supplier< BiConsumer< RealLocalizable, UnsignedIntType > >
	{
		@Override
		public BiConsumer< RealLocalizable, UnsignedIntType > get()
		{
			return new LocationToLabel();
		}

		private class LocationToLabel implements BiConsumer< RealLocalizable, UnsignedIntType >
		{
			private RadiusNeighborSearchOnKDTree< AS > search;
			private double radius;

			public LocationToLabel()
			{
				search = new RadiusNeighborSearchOnKDTree<>( kdTree );
				radius = 1.0;
			}

			@Override
			public void accept( RealLocalizable location, UnsignedIntType value )
			{
				search.search( location, radius, true );
				if ( search.numNeighbors() > 0 )
				{
					final Sampler< AS > sampler = search.getSampler( 0 );
					final AS annotatedRegion = sampler.get();
					value.setInteger( annotatedRegion.label() );
				}
				else
				{
					// background
					value.setInteger( 0 );
				}
			}
		}
	}



	private ArrayList< Integer > configureTimePoints()
	{
		final ArrayList< Integer > timePoints = new ArrayList<>();
		timePoints.add( 0 );
		return timePoints;
	}

	@Override
	public SourcePair< UnsignedIntType > getSourcePair()
	{
		return new DefaultSourcePair<>( source, volatileSource );
	}

	public String getName()
	{
		return name;
	}

	@Override
	public RealMaskRealInterval getMask( )
	{
		return mask;
	}

}
