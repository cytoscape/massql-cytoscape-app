package org.cytoscape.massql.app;

import java.util.Properties;

import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.cytoscape.equations.EquationParser;
import org.cytoscape.equations.Function;
import org.cytoscape.event.CyEventHelper;
import org.cytoscape.massql.app.command.MassqlCommandTaskFactory;
import org.cytoscape.massql.app.command.MassqlRunCommand;
import org.cytoscape.massql.app.equations.MassqlParseFunction;
import org.cytoscape.massql.app.ui.RunMassqlTaskFactory;
import org.cytoscape.service.util.AbstractCyActivator;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.task.NetworkTaskFactory;
import org.cytoscape.work.TaskFactory;

import static org.cytoscape.work.ServiceProperties.APPS_MENU;
import static org.cytoscape.work.ServiceProperties.COMMAND;
import static org.cytoscape.work.ServiceProperties.COMMAND_DESCRIPTION;
import static org.cytoscape.work.ServiceProperties.COMMAND_EXAMPLE_JSON;
import static org.cytoscape.work.ServiceProperties.COMMAND_LONG_DESCRIPTION;
import static org.cytoscape.work.ServiceProperties.COMMAND_NAMESPACE;
import static org.cytoscape.work.ServiceProperties.COMMAND_SUPPORTS_JSON;
import static org.cytoscape.work.ServiceProperties.ENABLE_FOR;
import static org.cytoscape.work.ServiceProperties.IN_CONTEXT_MENU;
import static org.cytoscape.work.ServiceProperties.IN_MENU_BAR;
import static org.cytoscape.work.ServiceProperties.IN_NETWORK_PANEL_CONTEXT_MENU;
import static org.cytoscape.work.ServiceProperties.MENU_GRAVITY;
import static org.cytoscape.work.ServiceProperties.PREFERRED_MENU;
import static org.cytoscape.work.ServiceProperties.TITLE;

/** OSGi entry point. Registers the app's services with the Cytoscape runtime. */
public class CyActivator extends AbstractCyActivator {

    private static final Logger LOGGER = LoggerFactory.getLogger(CyActivator.class);

    @Override
    public void start(BundleContext bc) {
        CyServiceRegistrar registrar = getService(bc, CyServiceRegistrar.class);

        registerEquationFunctions(bc);
        registerRunAction(bc, registrar);
        registerRunCommand(bc, registrar);

        LOGGER.info("MassQL app started");
    }

    /**
     * One registration reaches both menus: the menu-bar builder listens to every {@code
     * NetworkTaskFactory}, while the Network panel's context menu listens to the same service
     * filtered on {@code inNetworkPanelContextMenu}.
     */
    private void registerRunAction(BundleContext bc, CyServiceRegistrar registrar) {
        Properties props = new Properties();
        props.setProperty(TITLE, "Run MassQL...");
        props.setProperty(PREFERRED_MENU, APPS_MENU);
        props.setProperty(IN_NETWORK_PANEL_CONTEXT_MENU, "true");
        // The network view's own right-click menu is for acting on nodes and edges; this acts on
        // the whole table, so it belongs beside Rename and Export rather than there.
        props.setProperty(IN_CONTEXT_MENU, "false");
        props.setProperty(ENABLE_FOR, "network");
        props.setProperty(MENU_GRAVITY, "10.0");

        registerService(bc, new RunMassqlTaskFactory(registrar), NetworkTaskFactory.class, props);
    }

    /**
     * Publishes {@code massql run} to the Automation panel, CyREST and py4cytoscape.
     *
     * <p>{@code IN_MENU_BAR=false} is required, not tidiness: the menu-bar builder listens to every
     * {@code TaskFactory} without a filter and falls back to the Apps menu, so a command registered
     * without it also plants a second, unwanted menu item.
     */
    private void registerRunCommand(BundleContext bc, CyServiceRegistrar registrar) {
        Properties props = new Properties();
        props.setProperty(IN_MENU_BAR, "false");
        props.setProperty(COMMAND, MassqlRunCommand.NAME);
        props.setProperty(COMMAND_NAMESPACE, MassqlRunCommand.NAMESPACE);
        props.setProperty(COMMAND_DESCRIPTION, MassqlRunCommand.DESCRIPTION);
        props.setProperty(COMMAND_LONG_DESCRIPTION, MassqlRunCommand.LONG_DESCRIPTION);
        props.setProperty(COMMAND_SUPPORTS_JSON, "true");
        props.setProperty(COMMAND_EXAMPLE_JSON, MassqlRunCommand.EXAMPLE_JSON);

        registerService(bc, new MassqlCommandTaskFactory(registrar), TaskFactory.class, props);
    }

    /**
     * Registering a {@link Function} makes it available in the Formula Builder. The parser is
     * silenced around the registration because each one fires an event that asks every listener to
     * rebuild its function list.
     */
    private void registerEquationFunctions(BundleContext bc) {
        CyEventHelper events = getService(bc, CyEventHelper.class);
        EquationParser parser = getService(bc, EquationParser.class);

        events.silenceEventSource(parser);
        try {
            registerAllServices(bc, new MassqlParseFunction(), new Properties());
        } finally {
            events.unsilenceEventSource(parser);
        }
    }
}
