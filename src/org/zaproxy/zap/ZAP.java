/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2010 psiinon@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.zaproxy.zap;

import java.awt.EventQueue;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.UnsupportedLookAndFeelException;

import org.apache.commons.configuration.ConfigurationUtils;
import org.apache.commons.configuration.DefaultFileSystem;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.log4j.Appender;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.varia.NullAppender;
import org.jdesktop.swingx.JXErrorPane;
import org.jdesktop.swingx.error.ErrorInfo;
import org.parosproxy.paros.CommandLine;
import org.parosproxy.paros.Constant;
import org.parosproxy.paros.control.Control;
import org.parosproxy.paros.extension.option.OptionsParamView;
import org.parosproxy.paros.model.Model;
import org.parosproxy.paros.network.SSLConnector;
import org.parosproxy.paros.view.View;
import org.zaproxy.zap.control.AddOn;
import org.zaproxy.zap.control.AddOnCollection;
import org.zaproxy.zap.control.AddOnLoader;
import org.zaproxy.zap.control.AddOnRunIssuesUtils;
import org.zaproxy.zap.control.ControlOverrides;
import org.zaproxy.zap.control.ExtensionFactory;
import org.zaproxy.zap.eventBus.EventBus;
import org.zaproxy.zap.eventBus.SimpleEventBus;
import org.zaproxy.zap.extension.autoupdate.ExtensionAutoUpdate;
import org.zaproxy.zap.model.SessionUtils;
import org.zaproxy.zap.utils.ClassLoaderUtil;
import org.zaproxy.zap.utils.FontUtils;
import org.zaproxy.zap.utils.LocaleUtils;
import org.zaproxy.zap.view.LicenseFrame;
import org.zaproxy.zap.view.LocaleDialog;
import org.zaproxy.zap.view.ProxyDialog;
import org.zaproxy.zap.view.osxhandlers.OSXAboutHandler;
import org.zaproxy.zap.view.osxhandlers.OSXPreferencesHandler;
import org.zaproxy.zap.view.osxhandlers.OSXQuitHandler;

import com.apple.eawt.Application;

/**
 * 
 */
public class ZAP {

    private static Logger log = null;
    private CommandLine cmdLine = null;
    private static final EventBus eventBus = new SimpleEventBus();
    private static final Logger logger = Logger.getLogger(ZAP.class);

    static {
        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionLogger());

        // set SSLConnector as socketfactory in HttpClient.
        ProtocolSocketFactory sslFactory = null;
        try {
            final Protocol protocol = Protocol.getProtocol("https");
            sslFactory = protocol.getSocketFactory();

        } catch (final IllegalStateException e) {
            // Print the exception - log not yet initialised
            e.printStackTrace();
        }

        if (sslFactory == null || !(sslFactory instanceof SSLConnector)) {
            Protocol.registerProtocol("https", new Protocol("https",
                    (ProtocolSocketFactory) new SSLConnector(), 443));
        }
    }

    /**
     * Main method
     * 
     * @param args
     *            the arguments passed to the command line version
     * @throws Exception
     *             if something wrong happens
     */
    public static void main(String[] args) throws Exception {
        final ZAP zap = new ZAP();
        zap.init(args);

        // Nasty hack to prevent warning messages when running from the command
        // line
        NullAppender na = new NullAppender();
        Logger.getRootLogger().addAppender(na);
        Logger.getRootLogger().setLevel(Level.OFF);
        Logger.getLogger(ConfigurationUtils.class).addAppender(na);
        Logger.getLogger(DefaultFileSystem.class).addAppender(na);

        try {
            Constant.getInstance();

        } catch (final Throwable e) {
            // log not initialised yet
            System.out.println(e.getMessage());
            // throw e;
            System.exit(1);
        }

        final String msg = Constant.PROGRAM_NAME + " "
                + Constant.PROGRAM_VERSION + " started.";

        if (!zap.cmdLine.isGUI() && !zap.cmdLine.isDaemon()) {
            // Turn off log4j somewhere if not gui or daemon
            Logger.getRootLogger().removeAllAppenders();
            Logger.getRootLogger().addAppender(na);
            Logger.getRootLogger().setLevel(Level.OFF);

        } else {
            BasicConfigurator.configure();
        }

        if (zap.cmdLine.isGUI()) {
            setViewLocale(Constant.getLocale());
        }

        log = Logger.getLogger(ZAP.class);
        log.info(msg);

        try {
            zap.run();

        } catch (final Exception e) {
            log.fatal(e.getMessage(), e);
            // throw e;
            System.exit(1);
        }

    }

    private static void setViewLocale(Locale locale) {
        JComponent.setDefaultLocale(locale);
        JOptionPane.setDefaultLocale(locale);
    }

    /**
     * Initialization without dependence on any data model nor view creation.
     *
     * @param args
     */
    private void init(String[] args) {
        try {
            cmdLine = new CommandLine(args);

        } catch (final Exception e) {
            System.out.println(CommandLine.getHelpGeneral());
            System.exit(1);
        }

        try {
            // lang directory includes all of the language files
            final File langDir = new File(Constant.getZapInstall(), "lang");
            if (langDir.exists() && langDir.isDirectory()) {
                ClassLoaderUtil.addFile(langDir.getAbsolutePath());

            } else {
                System.out
                        .println("Warning: failed to load language files from "
                                + langDir.getAbsolutePath());
            }

            // Load all of the jars in the lib directory
            final File libDir = new File(Constant.getZapInstall(), "lib");
            if (libDir.exists() && libDir.isDirectory()) {
                final File[] files = libDir.listFiles();
                for (final File file : files) {
                    if (file.getName().toLowerCase(Locale.ENGLISH)
                            .endsWith("jar")) {
                        ClassLoaderUtil.addFile(file);
                    }
                }

            } else {
                System.out.println("Warning: failed to load jar files from "
                        + libDir.getAbsolutePath());
            }

        } catch (final IOException e) {
            System.out.println("Failed loading jars: " + e);
        }
    }

    /**
     * Override various handlers, so that About, Preferences, and Quit behave in
     * an OS X typical fashion.
     */
    public static boolean initMac() {
        logger.info("Initializing OS X specific settings, despite Apple's best efforts");

        // Attempt to load the apple classes
        Application app = Application.getApplication();

        // Try to set the dock image icon
        try {
            BufferedImage img = null;
            img = ImageIO.read(ZAP.class.getResource("/resource/zap1024x1024.png"));
            app.setDockIconImage(img);
        } catch (IOException e) {
            logger.info("Unable to find the ZAP icon for some weird reason");
        }
        
        // Set handlers for About and Preferences
        app.setAboutHandler(new OSXAboutHandler());
        app.setPreferencesHandler(new OSXPreferencesHandler());

        // Let's not forget to clean up our database mess when we Quit
        OSXQuitHandler quitHandler = new OSXQuitHandler();
        // quitHandler.removeZAPViewItem(view);  // TODO
        app.setQuitHandler(new OSXQuitHandler());

        // return true if fully initialized
        return (true);
    }

    private void run() throws Exception {

        final boolean isGUI = cmdLine.isGUI();
        Constant.setLowMemoryOption(cmdLine.isLowMem());

        boolean firstTime = false;
        if (isGUI) {
            try {
                // Get the systems Look and Feel
                UIManager.setLookAndFeel(UIManager
                        .getSystemLookAndFeelClassName());

                // Set Nimbus LaF if available and system is not OSX
                if (!Constant.isMacOsX()) {
                    for (final LookAndFeelInfo info : UIManager
                            .getInstalledLookAndFeels()) {
                        if ("Nimbus".equals(info.getName())) {
                            UIManager.setLookAndFeel(info.getClassName());
                            break;
                        }
                    }
                }
                // Set the various and sundry OS X-specific system properties
                else {
                    System.setProperty("apple.laf.useScreenMenuBar", "true");
                    System.setProperty("dock:name", "ZAP"); // Broken and unfixed; thanks, Apple
                    System.setProperty("com.apple.mrj.application.apple.menu.about.name", "ZAP"); // more thx
                    
                }

            } catch (final UnsupportedLookAndFeelException e) {
                // handle exception
            } catch (final ClassNotFoundException e) {
                // handle exception
            } catch (final InstantiationException e) {
                // handle exception
            } catch (final IllegalAccessException e) {
                // handle exception
            }

            firstTime = showLicense();
        }

        try {
            Model.getSingleton().init(this.getOverrides());

        } catch (final java.io.FileNotFoundException e) {
            if (isGUI) {
                JOptionPane.showMessageDialog(null,
                        Constant.messages.getString("start.db.error"),
                        Constant.messages.getString("start.title.error"),
                        JOptionPane.ERROR_MESSAGE);
            }

            System.out.println(Constant.messages.getString("start.db.error"));
            System.out.println(e.getLocalizedMessage());

            throw e;
        }

        FontUtils.setDefaultFont(Model.getSingleton().getOptionsParam()
                .getViewParam().getFontName(), Model.getSingleton()
                .getOptionsParam().getViewParam().getFontSize());

        Model.getSingleton().getOptionsParam().setGUI(isGUI);

        if (isGUI) {
            // Show the splash screen
            View.getSingleton().showSplashScreen();

            // Set the View options
            View.setDisplayOption(Model.getSingleton().getOptionsParam()
                    .getViewParam().getDisplayOption());

            // Prompt for language if not set
            String locale = Model.getSingleton().getOptionsParam()
                    .getViewParam().getConfigLocale();
            if (locale == null || locale.length() == 0) {

                // Dont use a parent of the MainFrame - that will initialise it
                // with English!
                final Locale userloc = determineUsersSystemLocale();
                if (userloc == null) {
                    // Only show the dialog, when the user's language can't be
                    // guessed.
                    setViewLocale(Constant.getSystemsLocale());
                    final LocaleDialog dialog = new LocaleDialog(null, true);
                    dialog.init(Model.getSingleton().getOptionsParam());
                    dialog.setVisible(true);

                } else {
                    Model.getSingleton().getOptionsParam().getViewParam()
                            .setLocale(userloc);
                }

                setViewLocale(createLocale(Model.getSingleton()
                        .getOptionsParam().getViewParam().getLocale()
                        .split("_")));
                Constant.setLocale(Model.getSingleton().getOptionsParam()
                        .getViewParam().getLocale());
                Model.getSingleton().getOptionsParam().getViewParam()
                        .getConfig().save();
            }

            // Prompt for proxy details if set
            if (Model.getSingleton().getOptionsParam().getConnectionParam()
                    .isProxyChainPrompt()) {
                final ProxyDialog dialog = new ProxyDialog(View.getSingleton()
                        .getMainFrame(), true);
                dialog.init(Model.getSingleton().getOptionsParam());
                dialog.setVisible(true);
            }

            try {
                // Times to run the GUI
                runGUI();
            } catch (Throwable e) {
                View.getSingleton().hideSplashScreen();
                if (!Constant.isDevBuild()) {
                    ErrorInfo errorInfo = new ErrorInfo(
                            Constant.messages
                                    .getString("start.gui.dialog.fatal.error.title"),
                            Constant.messages
                                    .getString("start.gui.dialog.fatal.error.message"),
                            null, null, e, null, null);
                    JXErrorPane errorPane = new JXErrorPane();
                    errorPane.setErrorInfo(errorInfo);
                    JXErrorPane.showDialog(View.getSingleton()
                            .getSplashScreen(), errorPane);
                }
                throw e;
            }

            warnAddOnsAndExtensionsNoLongerRunnable();

            if (firstTime) {
                // Disabled for now - we have too many popups occuring when you
                // first start up
                // be nice to have a clean start up wizard...
                // ExtensionHelp.showHelp();

            } else {
                // Dont auto check for updates the first time, no chance of any
                // proxy having been set
                final ExtensionAutoUpdate eau = (ExtensionAutoUpdate) Control
                        .getSingleton().getExtensionLoader()
                        .getExtension("ExtensionAutoUpdate");
                if (eau != null) {
                    eau.alertIfNewVersions();
                }
            }

        } else if (cmdLine.isDaemon()) {
            runDaemon();

        } else {
            runCommandLine();
        }

    }

    /**
     * Determines the {@link Locale} of the current user's system. It will match
     * the {@link Constant#getSystemsLocale()} with the available locales from
     * ZAPs translation files. It may return null, if the users system locale is
     * not in the list of available translations of ZAP.
     *
     * @return
     */
    private Locale determineUsersSystemLocale() {
        Locale userloc = null;
        final Locale systloc = Constant.getSystemsLocale();
        // first, try full match
        for (String ls : LocaleUtils.getAvailableLocales()) {
            String[] langArray = ls.split("_");
            if (langArray.length == 1) {
                if (systloc.getLanguage().equals(langArray[0])) {
                    userloc = systloc;
                    break;
                }
            }

            if (langArray.length == 2) {
                if (systloc.getLanguage().equals(langArray[0])
                        && systloc.getCountry().equals(langArray[1])) {
                    userloc = systloc;
                    break;
                }
            }

            if (langArray.length == 3) {
                if (systloc.getLanguage().equals(langArray[0])
                        && systloc.getCountry().equals(langArray[1])
                        && systloc.getVariant().equals(langArray[2])) {
                    userloc = systloc;
                    break;
                }
            }
        }

        if (userloc == null) {
            // second, try partial language match
            for (String ls : LocaleUtils.getAvailableLocales()) {
                String[] langArray = ls.split("_");
                if (systloc.getLanguage().equals(langArray[0])) {
                    userloc = createLocale(langArray);
                    break;
                }
            }
        }

        return userloc;
    }

    private static Locale createLocale(String[] localeFields) {
        if (localeFields == null || localeFields.length == 0) {
            return null;
        }

        Locale.Builder localeBuilder = new Locale.Builder();
        localeBuilder.setLanguage(localeFields[0]);

        if (localeFields.length >= 2) {
            localeBuilder.setRegion(localeFields[1]);
        }

        if (localeFields.length >= 3) {
            localeBuilder.setVariant(localeFields[2]);
        }

        return localeBuilder.build();
    }

    private ControlOverrides getOverrides() {
        ControlOverrides overrides = new ControlOverrides();
        overrides.setProxyPort(this.cmdLine.getPort());
        overrides.setProxyHost(this.cmdLine.getHost());
        overrides.setConfigs(this.cmdLine.getConfigs());
        overrides.setExperimentalDb(this.cmdLine.isExperimentalDb());
        return overrides;
    }

    private void runCommandLine() {
        int rc = 0;
        String help = "";

        Control.initSingletonWithoutView(this.getOverrides());
        final Control control = Control.getSingleton();

        warnAddOnsAndExtensionsNoLongerRunnable();

        // no view initialization
        try {
            control.getExtensionLoader().hookCommandLineListener(cmdLine);
            if (cmdLine.isEnabled(CommandLine.HELP)
                    || cmdLine.isEnabled(CommandLine.HELP2)) {
                help = cmdLine.getHelp();
                System.out.println(help);

            } else if (cmdLine.isReportVersion()) {
                System.out.println(Constant.PROGRAM_VERSION);

            } else {
                if (handleCmdLineSessionOptionsSynchronously(control)) {
                    control.runCommandLine();

                    try {
                        Thread.sleep(1000);

                    } catch (final InterruptedException e) {
                    }

                } else {
                    rc = 1;
                }
            }

        } catch (final Exception e) {
            log.error(e.getMessage(), e);
            System.out.println(e.getMessage());
            System.out.println();
            // Help is kind of useful too ;)
            help = cmdLine.getHelp();
            System.out.println(help);
            rc = 1;

        } finally {
            control.shutdown(Model.getSingleton().getOptionsParam()
                    .getDatabaseParam().isCompactDatabase());
            log.info(Constant.PROGRAM_TITLE + " terminated.");
        }

        System.exit(rc);
    }

    private static void warnAddOnsAndExtensionsNoLongerRunnable() {
        final AddOnLoader addOnLoader = ExtensionFactory.getAddOnLoader();
        List<String> idsAddOnsNoLongerRunning = addOnLoader
                .getIdsAddOnsWithRunningIssuesSinceLastRun();
        if (idsAddOnsNoLongerRunning.isEmpty()) {
            return;
        }

        List<AddOn> addOnsNoLongerRunning = new ArrayList<>(
                idsAddOnsNoLongerRunning.size());
        for (String id : idsAddOnsNoLongerRunning) {
            addOnsNoLongerRunning.add(addOnLoader.getAddOnCollection()
                    .getAddOn(id));
        }

        if (View.isInitialised()) {
            showWarningMessageAddOnsAndExtensionsNoLongerRunnable(
                    addOnLoader.getAddOnCollection(), addOnsNoLongerRunning);
        } else {
            for (AddOn addOn : addOnsNoLongerRunning) {
                AddOn.AddOnRunRequirements requirements = addOn
                        .calculateRunRequirements(addOnLoader
                                .getAddOnCollection().getAddOns());
                List<String> issues = AddOnRunIssuesUtils
                        .getRunningIssues(requirements);
                if (issues.isEmpty()) {
                    issues = AddOnRunIssuesUtils.getExtensionsRunningIssues(requirements);
                }

                log.warn("Add-on \"" + addOn.getId()
                        + "\" or its extensions will no longer be run until its requirements are restored: "
                        + issues);
            }
        }
    }

    private static void showWarningMessageAddOnsAndExtensionsNoLongerRunnable(
            final AddOnCollection installedAddOns,
            final List<AddOn> addOnsNoLongerRunning) {
        if (EventQueue.isDispatchThread()) {
            AddOnRunIssuesUtils.showWarningMessageAddOnsNotRunnable(
                    Constant.messages.getString("start.gui.warn.addOnsOrExtensionsNoLongerRunning"),
                    installedAddOns, addOnsNoLongerRunning);
        } else {
            EventQueue.invokeLater(new Runnable() {

                @Override
                public void run() {
                    showWarningMessageAddOnsAndExtensionsNoLongerRunnable(installedAddOns,
                            addOnsNoLongerRunning);
                }
            });
        }
    }

    /**
     * Run the GUI
     * 
     * @throws ClassNotFoundException
     * @throws Exception
     */
    private void runGUI() throws ClassNotFoundException, Exception {

        Control.initSingletonWithView(this.getOverrides());

        final Control control = Control.getSingleton();
        final View view = View.getSingleton();
        view.postInit();
        view.getMainFrame().setVisible(true);

        boolean createNewSession = true;
        if (cmdLine.isEnabled(CommandLine.SESSION)
                && cmdLine.isEnabled(CommandLine.NEW_SESSION)) {
            view.showWarningDialog(Constant.messages.getString(
                    "start.gui.cmdline.invalid.session.options",
                    CommandLine.SESSION, CommandLine.NEW_SESSION,
                    Constant.getZapHome()));

        } else if (cmdLine.isEnabled(CommandLine.SESSION)) {
            Path sessionPath = SessionUtils.getSessionPath(cmdLine
                    .getArgument(CommandLine.SESSION));
            if (!sessionPath.isAbsolute()) {
                view.showWarningDialog(Constant.messages.getString(
                        "start.gui.cmdline.session.absolute.path.required",
                        Constant.getZapHome()));

            } else {
                if (!Files.exists(sessionPath)) {
                    view.showWarningDialog(Constant.messages.getString(
                            "start.gui.cmdline.session.does.not.exist",
                            Constant.getZapHome()));

                } else {
                    createNewSession = !control.getMenuFileControl()
                            .openSession(
                                    sessionPath.toAbsolutePath().toString());
                }
            }

        } else if (cmdLine.isEnabled(CommandLine.NEW_SESSION)) {
            Path sessionPath = SessionUtils.getSessionPath(cmdLine
                    .getArgument(CommandLine.NEW_SESSION));
            if (!sessionPath.isAbsolute()) {
                view.showWarningDialog(Constant.messages.getString(
                        "start.gui.cmdline.session.absolute.path.required",
                        Constant.getZapHome()));

            } else {
                if (Files.exists(sessionPath)) {
                    view.showWarningDialog(Constant.messages.getString(
                            "start.gui.cmdline.newsession.already.exist",
                            Constant.getZapHome()));

                } else {
                    createNewSession = !control
                            .getMenuFileControl()
                            .newSession(sessionPath.toAbsolutePath().toString());
                }
            }
        }
        view.hideSplashScreen();

        if (createNewSession) {
            control.getMenuFileControl().newSession(false);
        }

        try {
            // Allow extensions to pick up command line args in GUI mode
            control.getExtensionLoader().hookCommandLineListener(cmdLine);
            control.runCommandLine();

        } catch (Exception e) {
            view.showWarningDialog(e.getMessage());
            log.error(e.getMessage(), e);
        }

        // Initialize OS X Everything
        if (Constant.isMacOsX()) {
            initMac();
        }

    }

    private void runDaemon() throws Exception {
        // start in a background thread
        final Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                View.setDaemon(true); // Prevents the View ever being
                                      // initialised
                Control.initSingletonWithoutView(getOverrides());
                Control control = Control.getSingleton();

                warnAddOnsAndExtensionsNoLongerRunnable();

                if (!handleCmdLineSessionOptionsSynchronously(control)) {
                    return;
                }

                try {
                    // Allow extensions to pick up command line args in daemon
                    // mode
                    control.getExtensionLoader().hookCommandLineListener(
                            cmdLine);
                    control.runCommandLine();

                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }

                // This is the only non-daemon thread, so should keep running
                // CoreAPI.handleApiAction uses System.exit to shutdown
                while (true) {
                    try {
                        Thread.sleep(100000);

                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }
            }
        });

        t.setName("ZAP-daemon");
        t.start();
    }

    private boolean handleCmdLineSessionOptionsSynchronously(Control control) {
        if (cmdLine.isEnabled(CommandLine.SESSION)
                && cmdLine.isEnabled(CommandLine.NEW_SESSION)) {
            System.err.println("Error: Invalid command line options: option '"
                    + CommandLine.SESSION + "' not allowed with option '"
                    + CommandLine.NEW_SESSION + "'");

            return false;
        }

        if (cmdLine.isEnabled(CommandLine.SESSION)) {
            Path sessionPath = SessionUtils.getSessionPath(cmdLine
                    .getArgument(CommandLine.SESSION));
            if (!sessionPath.isAbsolute()) {
                System.err
                        .println("Error: Invalid command line value: option '"
                                + CommandLine.SESSION
                                + "' requires an absolute path");

                return false;
            }

            String absolutePath = sessionPath.toAbsolutePath().toString();
            try {
                control.runCommandLineOpenSession(absolutePath);

            } catch (Exception e) {
                log.error(e.getMessage(), e);
                System.err.println("Failed to open session: " + absolutePath);
                e.printStackTrace(System.err);
                return false;
            }

        } else if (cmdLine.isEnabled(CommandLine.NEW_SESSION)) {
            Path sessionPath = SessionUtils.getSessionPath(cmdLine
                    .getArgument(CommandLine.NEW_SESSION));
            if (!sessionPath.isAbsolute()) {
                System.err
                        .println("Error: Invalid command line value: option '"
                                + CommandLine.NEW_SESSION
                                + "' requires an absolute path");

                return false;
            }

            String absolutePath = sessionPath.toAbsolutePath().toString();
            if (Files.exists(sessionPath)) {
                System.err
                        .println("Failed to create a new session, file already exists: "
                                + absolutePath);
                return false;
            }

            try {
                control.runCommandLineNewSession(absolutePath);

            } catch (Exception e) {
                log.error(e.getMessage(), e);
                System.err.println("Failed to create a new session: "
                        + absolutePath);
                e.printStackTrace(System.err);
                return false;
            }
        }

        return true;
    }

    private boolean showLicense() {
        boolean shown = false;

        File acceptedLicenseFile = new File(
                Constant.getInstance().ACCEPTED_LICENSE);

        if (!acceptedLicenseFile.exists()) {
            final LicenseFrame license = new LicenseFrame();
            license.setVisible(true);
            while (!license.isAccepted()) {
                try {
                    Thread.sleep(100);

                } catch (final InterruptedException e) {
                    log.error(e.getMessage(), e);
                }
            }
            shown = true;

            try {
                acceptedLicenseFile.createNewFile();

            } catch (final IOException ie) {
                JOptionPane.showMessageDialog(new JFrame(),
                        Constant.messages.getString("start.unknown.error"));
                log.error(ie.getMessage(), ie);
                System.exit(1);
            }
        }

        return shown;
    }

    public static EventBus getEventBus() {
        return eventBus;
    }

    private static final class UncaughtExceptionLogger implements
            Thread.UncaughtExceptionHandler {

        private static final Logger logger = Logger
                .getLogger(UncaughtExceptionLogger.class);

        private static boolean loggerConfigured = false;

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            if (!(e instanceof ThreadDeath)) {
                if (loggerConfigured || isLoggerConfigured()) {
                    if (e instanceof ClassCastException
                            && e.getMessage().endsWith(
                                    "cannot be cast to javax.swing.Painter")) {
                        // This is cause by initializing the ZAP on the main
                        // thread rather than the EDT
                        // Yes, we shoudlnt do that, but it works and will take
                        // significant effort to change to do it properly :/
                        // Log it as debug rather than error
                        logger.debug("Exception in thread \"" + t.getName()
                                + "\"", e);
                    } else {
                        logger.error("Exception in thread \"" + t.getName()
                                + "\"", e);
                    }

                } else {
                    System.err.println("Exception in thread \"" + t.getName()
                            + "\"");
                    e.printStackTrace();
                }
            }
        }

        private static boolean isLoggerConfigured() {
            if (loggerConfigured) {
                return true;
            }

            @SuppressWarnings("unchecked")
            Enumeration<Appender> appenders = LogManager.getRootLogger()
                    .getAllAppenders();
            if (appenders.hasMoreElements()) {
                loggerConfigured = true;
            } else {

                @SuppressWarnings("unchecked")
                Enumeration<Logger> loggers = LogManager.getCurrentLoggers();
                while (loggers.hasMoreElements()) {
                    Logger c = loggers.nextElement();
                    if (c.getAllAppenders().hasMoreElements()) {
                        loggerConfigured = true;
                        break;
                    }
                }
            }

            return loggerConfigured;
        }
    }
}
