package de.uni_freiburg.informatik.ultimate.plugins.generator.blockendcoding;

import java.util.ArrayList;
import java.util.List;


import de.uni_freiburg.informatik.ultimate.access.IObserver;
import de.uni_freiburg.informatik.ultimate.core.preferences.UltimatePreferenceInitializer;
import de.uni_freiburg.informatik.ultimate.ep.interfaces.IGenerator;
import de.uni_freiburg.informatik.ultimate.model.GraphType;
import de.uni_freiburg.informatik.ultimate.model.IElement;
import de.uni_freiburg.informatik.ultimate.plugins.generator.blockendcoding.preferences.PreferenceInitializer;

/**
 * Main class of Plug-In BlockEndcoding
 * 
 * 
 * TODO: refine comments
 * 
 */
public class BlockEncoding implements IGenerator {

	private static final String s_PLUGIN_NAME = Activator.s_PLUGIN_NAME;
	private static final String s_PLUGIN_ID = Activator.s_PLUGIN_ID;

	private MinModelConversionObserver m_ConversionObserver;
	private BlockEncodingObserver m_BlockEncodingObserver;
	private GraphType m_InputDefinition;

	// private static Logger s_Logger =
	// UltimateServices.getInstance().getLogger(Activator.s_PLUGIN_ID);

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * de.uni_freiburg.informatik.ultimate.ep.interfaces.IRCPPlugin#getName()
	 */
	@Override
	public String getName() {
		return s_PLUGIN_NAME;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * de.uni_freiburg.informatik.ultimate.ep.interfaces.IRCPPlugin#getPluginID
	 * ()
	 */
	@Override
	public String getPluginID() {
		return s_PLUGIN_ID;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * de.uni_freiburg.informatik.ultimate.ep.interfaces.IRCPPlugin#init(java
	 * .lang.Object)
	 */
	@Override
	public int init(Object param) {
		m_ConversionObserver = new MinModelConversionObserver();
		m_BlockEncodingObserver = new BlockEncodingObserver();
		return 0;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * de.uni_freiburg.informatik.ultimate.ep.interfaces.ITool#getQueryKeyword()
	 */
	@Override
	public QueryKeyword getQueryKeyword() {
		return QueryKeyword.LAST;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * de.uni_freiburg.informatik.ultimate.ep.interfaces.ITool#getDesiredToolID
	 * ()
	 */
	@Override
	public List<String> getDesiredToolID() {
		return null;
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * de.uni_freiburg.informatik.ultimate.ep.interfaces.ITool#setInputDefinition
	 * (de.uni_freiburg.informatik.ultimate.model.GraphType)
	 */
	@Override
	public void setInputDefinition(GraphType graphType) {
		this.m_InputDefinition = graphType;
	}

	// @Override
	public List<IObserver> getObservers() {
		ArrayList<IObserver> observers = new ArrayList<IObserver>();
		observers.add(m_BlockEncodingObserver);
		observers.add(m_ConversionObserver);
		return observers;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.uni_freiburg.informatik.ultimate.ep.interfaces.IModifyingTool#
	 * getOutputDefinition()
	 */
	public GraphType getOutputDefinition() {
		if (m_ConversionObserver.getRoot() == null) {
			return new GraphType("BlockEncodedModel",
					m_InputDefinition.getType(),
					m_InputDefinition.getFileNames());
		}
		return new GraphType(Activator.s_PLUGIN_ID,
				m_InputDefinition.getType(), m_InputDefinition.getFileNames());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * de.uni_freiburg.informatik.ultimate.ep.interfaces.IGenerator#getModel()
	 */
	@Override
	public IElement getModel() {
		if (m_ConversionObserver.getRoot() == null) {
			return m_BlockEncodingObserver.getRoot();
		}
		return this.m_ConversionObserver.getRoot();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * de.uni_freiburg.informatik.ultimate.ep.interfaces.ITool#getRequireGui()
	 */
	@Override
	public boolean isGuiRequired() {
		return false;
	}


	@Override
	public UltimatePreferenceInitializer getPreferences() {
		return new PreferenceInitializer();
	}
}
