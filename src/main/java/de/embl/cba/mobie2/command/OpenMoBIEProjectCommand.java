package de.embl.cba.mobie2.command;

import de.embl.cba.mobie.ui.MoBIEOptions;
import de.embl.cba.mobie.ui.MoBIE;
import de.embl.cba.mobie2.MoBIE2;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.IOException;


@Plugin(type = Command.class, menuPath = "Plugins>MoBIE2>Open>Open MoBIE Project..." )
public class OpenMoBIEProjectCommand implements Command
{
	@Parameter ( label = "Project Location" )
	public String projectLocation = "https://github.com/mobie/platybrowser-datasets";

	@Override
	public void run()
	{
		MoBIEOptions options = MoBIEOptions.options();

		if ( projectLocation.startsWith( "http" ) )
			options = options.imageDataStorageModality( MoBIEOptions.ImageDataStorageModality.S3 );

		try
		{
			new MoBIE2( projectLocation, options );
		}
		catch ( IOException e )
		{
			e.printStackTrace();
		}
	}
}