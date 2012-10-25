/**
 * Copyright JRebirth.org © 2011-2012 
 * Contact : sebastien.bordes@jrebirth.org
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jrebirth.core.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import javafx.scene.layout.Pane;

import org.jrebirth.core.application.JRebirthApplication;
import org.jrebirth.core.command.basic.ShowModelWaveBuilder;
import org.jrebirth.core.event.JRebirthLogger;
import org.jrebirth.core.exception.CoreException;
import org.jrebirth.core.exception.JRebirthThreadException;
import org.jrebirth.core.facade.GlobalFacade;
import org.jrebirth.core.facade.GlobalFacadeBase;

/**
 * The class <strong>JRebirthThread</strong>.
 * 
 * @author Sébastien Bordes
 */
public final class JRebirthThread extends Thread {

    /** The JRebirth Thread name. */
    public static final String NAME = "JRebirth Thread";

    /** The unique instance of the current class. */
    private static JRebirthThread internalThread;

    /** The Global Facade object that handle other sub facade. */
    private transient GlobalFacade facade;

    /** The javaFX application that launch this thread. */
    private transient JRebirthApplication application;

    /** The list of tasks to execute, all access MUST BE synchronized. */
    // private final List<Runnable> tasks;

    /** The list of tasks queued waiting to be processed, all access MUST BE synchronized. */
    private final List<Runnable> queuedTasks;

    /** The list of tasks being processed, all access MUST BE synchronized. */
    private final List<Runnable> processingTasks;

    /** Flag to stop the infinite loop that process JRebirth Events. */
    private boolean infiniteLoop = true;

    /** Flag that indicate that the closure must be forced. */
    private boolean forceClose;

    /**
     * private final boolean readyToShutdown = false;
     * 
     * /** Build the JRebirth Thread.
     */
    private JRebirthThread() {
        super(NAME);

        // Daemonize this thread, thus it will be killed with the main JavaFX
        // thread
        setDaemon(true);

        // Initialize the queue
        this.queuedTasks = new ArrayList<>();
        this.processingTasks = new ArrayList<>();
    }

    /**
     * Run into thread pool.
     * 
     * If a slot is available the task will be run immediately.<br />
     * Otherwise it will run as soon as a slot will be available according to the existing task queue
     * 
     * @param runnable the task to run
     */
    public void runNow(final Runnable runnable) {
        final Future<?> future = getFacade().getExecutorService().submit(runnable);
        // TODO log or delete
    }

    /**
     * Run this task as soon as possible. Enqueue the task to be run at the next event pulse. Run ii into the JRebirth Thread
     * 
     * @param runnable the task to run
     */
    public void runAsap(final Runnable runnable) {
        // Synchronize the queue !
        synchronized (this.queuedTasks) {
            this.queuedTasks.notifyAll();
            this.queuedTasks.add(runnable);
        }
    }

    /**
     * Launch the JRebirth thread.
     * 
     * @param application the javaFX application instance
     */
    public void launch(final JRebirthApplication application) {

        // LInk the current application
        this.application = application;
        // Build the global facade at startup
        this.facade = new GlobalFacadeBase(application);

        // Start the thread (infinite loop)
        start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {

        try {
            launchFirstView();
        } catch (final CoreException e) {
            getFacade().getLogger().logException(e);
        }

        while (this.infiniteLoop) {
            try {

                // Need to sort tasks to launch by priority
                // Collections.sort(tasks);

                // Synchronize the re-copy operation
                synchronized (this.queuedTasks) {
                    // Copy enqueued tasks into processing list to run them
                    this.processingTasks.addAll(this.queuedTasks);
                    // Purge the current queue
                    this.queuedTasks.clear();
                }

                // Synchronize the queue !
                synchronized (this.processingTasks) {

                    // Run all tasks that are waiting to be processed
                    for (final Runnable r : this.processingTasks) {
                        if (!this.forceClose) {
                            r.run();
                        }
                    }
                    // Remove all task processed
                    this.processingTasks.clear();
                }

                synchronized (this.queuedTasks) {
                    // Pause this thread during 20ms to let other thread adding some
                    // task into the queue
                    // Thread.sleep(20);

                    if (this.queuedTasks.size() == 0) {
                        // Pause the JRebirth Thread no more than 500ms
                        // it will be woke up if any task is added to the queue
                        // Obviously if a task has been added during the unqueue of the processingTask queue,
                        // the waiting is canceled
                        this.queuedTasks.wait(500);
                    }
                }

            } catch (final InterruptedException e) {
                this.facade.getLogger().error("An exception occured into the JRebirth Thread");
                this.facade.getLogger().logException(e);
            }

        }
        // Shutdown the application more properly
        shutdown();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void interrupt() {
        super.interrupt();

        // Release all resources
        shutdown();
    }

    /**
     * 
     */
    public void close() {

        // Infinite loop is till active
        if (this.infiniteLoop) {
            // First attempt to close the application
            this.infiniteLoop = false;
        } else {
            // N-th attempt to close the application
            this.forceClose = true;

            // All Task Queues are cleared
            this.queuedTasks.clear();
            this.processingTasks.clear();

        }

    }

    /**
     * Release all resources.
     */
    private void shutdown() {
        try {
            this.facade.stop();
            this.facade = null;
        } catch (final CoreException e) {
            JRebirthLogger.getInstance().logException(e);
        }
    }

    /**
     * Launch the first view by adding it into the root node.
     * 
     * @throws CoreException if the first class was not found
     */
    protected void launchFirstView() throws CoreException {

        try {

            getFacade().getNotifier().sendWave(
                    ShowModelWaveBuilder.create()
                            .parentNode((Pane) JRebirthThread.this.application.getScene().getRoot())
                            .modelClass(this.application.getFirstModelClass())
                            .build()
                    );

        } catch (final JRebirthThreadException e) {
            this.facade.getLogger().error("An exception occured while creating and attaching the first view");
            this.facade.getLogger().logException(e);
            // Impossible case, unless someone override JRebirthThread class
            throw new CoreException("launchFirstView method was called outside JIT.", e);
        }
    }

    /**
     * @return Returns the application.
     */
    public JRebirthApplication getApplication() {
        return this.application;
    }

    /**
     * @return Returns the facade.
     */
    public GlobalFacade getFacade() {
        return this.facade;
    }

    /**
     * Return the JRebirthThread used to manage facades and waves.
     * 
     * @return the JRebirthThread
     */
    public static JRebirthThread getThread() {
        synchronized (JRebirthThread.class) {
            if (internalThread == null) {
                internalThread = new JRebirthThread();
            }
        }
        return internalThread;
    }

    /**
     * Return true if we are into the JRebirth Thread.
     * 
     * @return true if currentThread == JRebirthThread
     */
    public static boolean isJRebirthThread() {
        return NAME.equals(Thread.currentThread().getName());
    }

    /**
     * Check if the current code is executed into the JRebirthThread otherwise throws an exception.
     * 
     * @throws JRebirthThreadException if the we are into the wrong thread
     */
    public static void checkJRebirthThread() throws JRebirthThreadException {
        if (!isJRebirthThread()) {
            throw new JRebirthThreadException();
        }
    }

    /**
     * Run a task as soon as possible.
     * 
     * @param runnable the task to run
     */
    public static void runLater(final Runnable runnable) {
        internalThread.runAsap(runnable);
    }

    /**
     * Run a task immedialtely if a slot is available into the shared thread pool.
     * 
     * @param runnable the task to run
     */
    public static void runIntoThreadPool(final Runnable runnable) {
        internalThread.runNow(runnable);
    }

}
