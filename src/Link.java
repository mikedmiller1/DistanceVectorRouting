import java.io.*;

public class Link implements Serializable
{
	/*
	 * Properties
	 */
	private static final long serialVersionUID = 1L;
	public Node Node;
	public Double Cost;
	
	
	
	/*
	 * Default constructor
	 */
	public Link()
	{
		this( new Node(), 0.0 );
	}
	
	
	
	/*
	 * Constructor
	 */
	public Link( Node Node, Double Cost )
	{
		this.Node = Node;
		this.Cost = Cost;
	}
	
	
	
	/*
	 * Constructor
	 */
	public Link( String LinkString )
	{
		// Parse the properties from the string
		String[] LinkStringParsed = LinkString.split( " " );
		
		String Name = LinkStringParsed[ 0 ];
		String IpAddress = LinkStringParsed[ 1 ];
		int Port = Integer.parseInt( LinkStringParsed[ 2 ] );
		this.Cost = Double.parseDouble( LinkStringParsed[ 3 ] );
		
		// Create the node
		this.Node = new Node( Name, IpAddress, Port );
	}
	
	
	
	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString()
	{
        return Node.Name + " " + Node.IpAddress + " " + Node.Port + " " + Cost;
	}
	
}
