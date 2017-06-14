import java.io.Serializable;

public class Link implements Serializable
{
	
	private static final long serialVersionUID = 1L;
	public String Name;
	public Double Cost;
	public String IpAddress;
	public int Port;
	
	
	
	public Link( String LinkString )
	{
		// Parse the properties from the string
		String[] LinkStringParsed = LinkString.split( " " );
		
		Name = LinkStringParsed[ 0 ];
		IpAddress = LinkStringParsed[ 1 ];
		Port = Integer.parseInt( LinkStringParsed[ 2 ] );
		Cost = Double.parseDouble( LinkStringParsed[ 3 ] );
	}
	
	
	
	@Override
	public String toString()
	{
        return Name + " " + IpAddress + " " + Port + " " + Cost;
	}
	
}
