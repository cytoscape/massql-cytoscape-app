package org.cytoscape.massql.app.command;

import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.work.AbstractTaskFactory;
import org.cytoscape.work.TaskIterator;

/** Supplies the task behind {@code massql run}. */
public class MassqlCommandTaskFactory extends AbstractTaskFactory {

    private final CyServiceRegistrar registrar;

    public MassqlCommandTaskFactory(CyServiceRegistrar registrar) {
        this.registrar = registrar;
    }

    @Override
    public TaskIterator createTaskIterator() {
        return new TaskIterator(new MassqlCommandTask(registrar));
    }
}
