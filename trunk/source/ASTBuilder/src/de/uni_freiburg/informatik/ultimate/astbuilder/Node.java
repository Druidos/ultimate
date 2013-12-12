/* Node -- Automatically generated by TreeBuilder */

package de.uni_freiburg.informatik.ultimate.astbuilder;
import java.util.HashSet;

/**
 * Represents a node.
 */
public class Node {
    /**
     * The name of this node.
     */
    String name;

    /**
     * The parent of this node.
     */
    String parent;

    /**
     * The comment of this node.
     */
    String comment;

    /**
     * The used types of this node.
     */
    HashSet<String> usedTypes;

    /**
     * True iff this node is abstract.
     */
    boolean isAbstract;

    /**
     * The parameters of this node.
     */
    Parameter[] parameters;

    /**
     * The constructor taking initial values.
     * @param name the name of this node.
     * @param parent the parent of this node.
     * @param comment the comment of this node.
     * @param usedTypes the used types of this node.
     * @param isAbstract true iff this node is abstract.
     * @param parameters the parameters of this node.
     */
    public Node(String name, String parent, String comment, HashSet<String> usedTypes, boolean isAbstract, Parameter[] parameters) {
        super();
        this.name = name;
        this.parent = parent;
        this.comment = comment;
        this.usedTypes = usedTypes;
        this.isAbstract = isAbstract;
        this.parameters = parameters;
    }

    /**
     * Returns a textual description of this object.
     */
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("Node").append('[');
        sb.append(name);
        sb.append(',').append(parent);
        sb.append(',').append(comment);
        sb.append(',').append(usedTypes);
        sb.append(',').append(isAbstract);
        sb.append(',');
        if (parameters == null) {
            sb.append("null");
        } else {
            sb.append('[');
            for(int i1 = 0; i1 < parameters.length; i1++) {
                if (i1 > 0) sb.append(',');
                    sb.append(parameters[i1]);
            }
            sb.append(']');
        }
        return sb.append(']').toString();
    }

    /**
     * Gets the name of this node.
     * @return the name of this node.
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the parent of this node.
     * @return the parent of this node.
     */
    public String getParent() {
        return parent;
    }

    /**
     * Gets the comment of this node.
     * @return the comment of this node.
     */
    public String getComment() {
        return comment;
    }

    /**
     * Gets the used types of this node.
     * @return the used types of this node.
     */
    public HashSet<String> getUsedTypes() {
        return usedTypes;
    }

    /**
     * Checks iff this node is abstract.
     * @return true iff this node is abstract.
     */
    public boolean isAbstract() {
        return isAbstract;
    }

    /**
     * Gets the parameters of this node.
     * @return the parameters of this node.
     */
    public Parameter[] getParameters() {
        return parameters;
    }
}
