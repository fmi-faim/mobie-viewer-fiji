package org.embl.mobie.viewer;

import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.type.volatiles.VolatileUnsignedIntType;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public abstract class SourceNameEncoder
{
	private static Map< String, Long > nameToLong = new HashMap< String, Long >();
	private static Map< Long, String > longToName = new HashMap< Long, String >();
	private static long count = 0;

	public static Integer valueBits = 16;

	public static synchronized void addNames( Collection< String > names )
	{
		for ( String name : names )
		{
			addName( name );
		}
	}

	public static void addName( String name )
	{
		if ( ! nameToLong.containsKey( name ) )
		{
			nameToLong.put( name, count );
			longToName.put( count, name );
			count++;
		}
	}

	public static String getName( final UnsignedIntType unsignedIntType )
	{
		final long imageIndex = unsignedIntType.get() >> valueBits;
		final String name = longToName.get( imageIndex );
		return name;
	}

	public static String getName( final VolatileUnsignedIntType unsignedIntType )
	{
		final long imageIndex = unsignedIntType.get().get() >> valueBits;
		final String name = longToName.get( imageIndex );
		return name;
	}

	public static long getValue( final UnsignedIntType unsignedIntType )
	{
		final long value = unsignedIntType.get() & 0x0000FFFF;
		return value;
	}

	public static long getValue( final VolatileUnsignedIntType unsignedIntType )
	{
		final long value = unsignedIntType.get().get() & 0x0000FFFF;
		return value;
	}

	public static void encodeName( final UnsignedIntType value, final String name )
	{
		final long l = value.get();
		final long encoded = l + ( nameToLong.get( name ) << valueBits );
		value.set( encoded );
		final long value1 = getValue( value );
	}

}