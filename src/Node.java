import java.io.*;

public class Node implements Serializable
{
	/*
	 * Properties
	 */
	private static final long serialVersionUID = 1L;
	public String Name;
	public String IpAddress;
	public int Port;
	
	
	
	/*
	 * Default constructor
	 */
	public Node()
	{
		this( "", "", 0 );
	}
	
	
	
	/*
	 * Constructor
	 */
	public Node( String Name, String IpAddress, int Port )
	{
		this.Name = Name;
		this.IpAddress = IpAddress;
		this.Port = Port;
	}
	
	
	
	/*
	 * Copy constructor
	 */
	public Node( Node original )
	{
		this.Name = original.Name;
		this.IpAddress = original.IpAddress;
		this.Port = original.Port;
	}
	
	
	
	/*
	 * Returns a copy of the object.
	 */
	public Node DeepCopy()
	{
		return new Node( this );
	}
	
	
	
	/*
	 * Override of default toString()
	 * Returns the node name.
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString()
	{
		return this.Name;
	}
}
