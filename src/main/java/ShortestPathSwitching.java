package edu.brown.cs.sdn.apps.sps;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.brown.cs.sdn.apps.util.Host;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitch.PortChangeType;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceListener;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryListener;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.routing.Link;

public class ShortestPathSwitching implements IFloodlightModule, IOFSwitchListener,
        ILinkDiscoveryListener, IDeviceListener, InterfaceShortestPathSwitching {
    public static final String MODULE_NAME = ShortestPathSwitching.class.getSimpleName();

    // Interface to the logging system
    private static Logger log = LoggerFactory.getLogger(MODULE_NAME);

    // Interface to Floodlight core for interacting with connected switches
    private IFloodlightProviderService floodlightProv;

    // Interface to link discovery service
    private ILinkDiscoveryService linkDiscProv;

    // Interface to device manager service
    private IDeviceService deviceProv;

    // Switch table in which rules should be installed
    private byte table;

    // Map of hosts to devices
    private Map<IDevice, Host> knownHosts;

    // Hash table containing the shortest Paths for each switch to every other switch in the network
    public HashMap<IOFSwitch, HashMap<IOFSwitch, IOFSwitch>> shortestPaths;

    /**
     * Loads dependencies and initializes data structures.
     */
    @Override
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {
        log.info(String.format("Initializing %s...", MODULE_NAME));
        Map<String, String> config = context.getConfigParams(this);
        this.table = Byte.parseByte(config.get("table"));

        this.floodlightProv = context.getServiceImpl(
                IFloodlightProviderService.class);
        this.linkDiscProv = context.getServiceImpl(ILinkDiscoveryService.class);
        this.deviceProv = context.getServiceImpl(IDeviceService.class);

        this.knownHosts = new ConcurrentHashMap<IDevice, Host>();

        /*********************************************************************/
        /* TODO: Initialize other class variables, if necessary              */

        /*********************************************************************/
        this.shortestPaths = new HashMap<IOFSwitch, HashMap<IOFSwitch, IOFSwitch>>();
    }

    /**
     * Subscribes to events and performs other startup tasks.
     */
    @Override
    public void startUp(FloodlightModuleContext context)
            throws FloodlightModuleException {
        log.info(String.format("Starting %s...", MODULE_NAME));
        this.floodlightProv.addOFSwitchListener(this);
        this.linkDiscProv.addListener(this);
        this.deviceProv.addListener(this);

        /*********************************************************************/
        /* TODO: Perform other tasks, if necessary                           */

        /*********************************************************************/
    }

    /**
     * Get the table in which this application installs rules.
     */
    public byte getTable() {
        return this.table;
    }

    /**
     * Get a list of all known hosts in the network.
     */
    private Collection<Host> getHosts() {
        return this.knownHosts.values();
    }

    /**
     * Get a map of all active switches in the network. Switch DPID is used as
     * the key.
     */
    private Map<Long, IOFSwitch> getSwitches() {
        return floodlightProv.getAllSwitchMap();
    }

    /**
     * Get a list of all active links in the network.
     */
    private Collection<Link> getLinks() {
        return linkDiscProv.getLinks().keySet();
    }

    /**
     * Event handler called when a host joins the network.
     *
     * @param device information about the host
     */
    @Override
    public void deviceAdded(IDevice device) {
        Host host = new Host(device, this.floodlightProv);
        // We only care about a new host if we know its IP
        if (host.getIPv4Address() != null) {
            log.info(String.format("Host %s added", host.getName()));
            this.knownHosts.put(device, host);

            /*****************************************************************/
            /* TODO: Update routing: add rules to route to new host          */

            /*****************************************************************/
            logData();
        }
    }

    /**
     * Event handler called when a host is no longer attached to a switch.
     *
     * @param device information about the host
     */
    @Override
    public void deviceRemoved(IDevice device) {
        Host host = this.knownHosts.get(device);
        if (null == host) {
            host = new Host(device, this.floodlightProv);
            this.knownHosts.put(device, host);
        }

        log.info(String.format("Host %s is no longer attached to a switch",
                host.getName()));

        /*********************************************************************/
        /* TODO: Update routing: remove rules to route to host               */

        /*********************************************************************/
        logData();
    }

    /**
     * Event handler called when a host moves within the network.
     *
     * @param device information about the host
     */
    @Override
    public void deviceMoved(IDevice device) {
        Host host = this.knownHosts.get(device);
        if (null == host) {
            host = new Host(device, this.floodlightProv);
            this.knownHosts.put(device, host);
        }

        if (!host.isAttachedToSwitch()) {
            this.deviceRemoved(device);
            return;
        }
        log.info(String.format("Host %s moved to s%d:%d", host.getName(),
                host.getSwitch().getId(), host.getPort()));

        /*********************************************************************/
        /* TODO: Update routing: change rules to route to host               */

        /*********************************************************************/
        logData();
    }

    /**
     * Event handler called when a switch joins the network.
     *
     * @param DPID for the switch
     */
    @Override
    public void switchAdded(long switchId) {
        IOFSwitch sw = this.floodlightProv.getSwitch(switchId);
        log.info(String.format("Switch s%d added", switchId));

        /*********************************************************************/
        /* TODO: Update routing: change routing rules for all hosts          */

        /*********************************************************************/
        logData();
    }

    /**
     * Event handler called when a switch leaves the network.
     *
     * @param DPID for the switch
     */
    @Override
    public void switchRemoved(long switchId) {
        IOFSwitch sw = this.floodlightProv.getSwitch(switchId);
        log.info(String.format("Switch s%d removed", switchId));

        /*********************************************************************/
        /* TODO: Update routing: change routing rules for all hosts          */

        /*********************************************************************/
        logData();
    }

    /**
     * Event handler called when multiple links go up or down.
     *
     * @param updateList information about the change in each link's state
     */
    @Override
    public void linkDiscoveryUpdate(List<LDUpdate> updateList) {
        for (LDUpdate update : updateList) {
            // If we only know the switch & port for one end of the link, then
            // the link must be from a switch to a host
            if (0 == update.getDst()) {
                log.info(String.format("Link s%s:%d -> host updated",
                        update.getSrc(), update.getSrcPort()));
            }
            // Otherwise, the link is between two switches
            else {
                log.info(String.format("Link s%s:%d -> %s:%d updated",
                        update.getSrc(), update.getSrcPort(),
                        update.getDst(), update.getDstPort()));
            }
        }

        /*********************************************************************/
        /* TODO: Update routing: change routing rules for all hosts          */

        /*********************************************************************/
        logData();
    }

    /**
     * Event handler called when link goes up or down.
     *
     * @param update information about the change in link state
     */
    @Override
    public void linkDiscoveryUpdate(LDUpdate update) {
        this.linkDiscoveryUpdate(Arrays.asList(update));
    }

    /**
     * Event handler called when the IP address of a host changes.
     *
     * @param device information about the host
     */
    @Override
    public void deviceIPV4AddrChanged(IDevice device) {
        this.deviceAdded(device);
    }

    /**
     * Event handler called when the VLAN of a host changes.
     *
     * @param device information about the host
     */
    @Override
    public void deviceVlanChanged(IDevice device) { /* Nothing we need to do, since we're not using VLANs */ }

    /**
     * Event handler called when the controller becomes the master for a switch.
     *
     * @param DPID for the switch
     */
    @Override
    public void switchActivated(long switchId) { /* Nothing we need to do, since we're not switching controller roles */ }

    /**
     * Event handler called when some attribute of a switch changes.
     *
     * @param DPID for the switch
     */
    @Override
    public void switchChanged(long switchId) { /* Nothing we need to do */ }

    /**
     * Event handler called when a port on a switch goes up or down, or is
     * added or removed.
     *
     * @param DPID for the switch
     * @param port the port on the switch whose status changed
     * @param type the type of status change (up, down, add, remove)
     */
    @Override
    public void switchPortChanged(long switchId, ImmutablePort port,
                                  PortChangeType type) { /* Nothing we need to do, since we'll get a linkDiscoveryUpdate event */ }

    /**
     * Gets a name for this module.
     *
     * @return name for this module
     */
    @Override
    public String getName() {
        return this.MODULE_NAME;
    }

    /**
     * Check if events must be passed to another module before this module is
     * notified of the event.
     */
    @Override
    public boolean isCallbackOrderingPrereq(String type, String name) {
        return false;
    }

    /**
     * Check if events must be passed to another module after this module has
     * been notified of the event.
     */
    @Override
    public boolean isCallbackOrderingPostreq(String type, String name) {
        return false;
    }

    /**
     * Tell the module system which services we provide.
     */
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> services =
                new ArrayList<Class<? extends IFloodlightService>>();
        services.add(InterfaceShortestPathSwitching.class);
        return services;
    }

    /**
     * Tell the module system which services we implement.
     */
    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
    getServiceImpls() {
        Map<Class<? extends IFloodlightService>, IFloodlightService> services =
                new HashMap<Class<? extends IFloodlightService>,
                        IFloodlightService>();
        // We are the class that implements the service
        services.put(InterfaceShortestPathSwitching.class, this);
        return services;
    }

    /**
     * Tell the module system which modules we depend on.
     */
    @Override
    public Collection<Class<? extends IFloodlightService>>
    getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> modules =
                new ArrayList<Class<? extends IFloodlightService>>();
        modules.add(IFloodlightProviderService.class);
        modules.add(ILinkDiscoveryService.class);
        modules.add(IDeviceService.class);
        return modules;
    }

    private void logData() {
        log.info("##################### LOG DATA #######################################");
        log.info("#############TABLE#############");
        log.info(String.valueOf(this.table));
        log.info("");
        printHosts(this.getHosts());
        printSwitches(this.getSwitches());
        printLinks(this.getLinks());
    }

    private void printHosts(Collection<Host> hosts) {

        log.info("#############HOSTS#############");
        for (Host host : hosts) {
            log.info(host.getName());
            log.info("MACAddress: " + host.getMACAddress());
            log.info("IPv4: " + host.getIPv4Address());
            log.info("Port: " + host.getPort());
            log.info("Switch: " + host.getSwitch());
        }
        log.info("");
    }

    private void printSwitches(Map<Long, IOFSwitch> switches) {

        log.info("#############Switches#############");
        Iterator<Map.Entry<Long, IOFSwitch>> iterator = switches.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, IOFSwitch> _switch = iterator.next();
            log.info(_switch.getValue().getStringId());
        }
        log.info("");
    }

    private void printLinks(Collection<Link> links) {

        log.info("#############Links#############");
        for (Link link : links) {
            log.info(String.valueOf(link));
            log.info("Src: " + link.getSrc());
            log.info("SrcPort: " + link.getSrcPort());
            log.info("Dst: " + link.getDst());
            log.info("DstPort: " + link.getDstPort());
        }
        log.info("");
    }

    /*
    Code implemented at:
    https://github.com/vnatesh/SDN-Controller/blob/b762e3476a6cc85b72b5d083096b2c17023f6ac6/ShortestPathSwitching.java#L101

		Calculates shortest paths from each switch to all other switches using Dijkstra's
		shortest path algorithm. Results are stored in a hash table of the form;

			{switch1 : {switch1 : null, switch2 : switch1, ...},
			switch2 : {switch1: switch2, switch2 : null, ...}, ...}

		The inner hash tables store the parent switches for each switch along the path
		from switch x to all other switches. Link costs are all assumed to be 1.

		Variable d represents the distance vector. Variable q represents the priority
		queue used in dijkstra.
	*/
    public HashMap<IOFSwitch, HashMap<IOFSwitch, IOFSwitch>> dijkstraPaths() {

        Collection<IOFSwitch> switches = getSwitches().values();
        HashMap<IOFSwitch, HashMap<IOFSwitch, IOFSwitch>> shortestPaths = new HashMap<IOFSwitch, HashMap<IOFSwitch, IOFSwitch>>();

        for(IOFSwitch v : switches) {

            // Initialize distances, parent switch for all switches.
            // q represents a priority queue to track switches that haven't
            // been processed by the algorithm yet
            HashMap<IOFSwitch, Integer> d = new HashMap<IOFSwitch, Integer>();
            HashMap<IOFSwitch, IOFSwitch> parent = new HashMap<IOFSwitch, IOFSwitch>();
            HashMap<IOFSwitch, Integer> q = new HashMap<IOFSwitch, Integer>();

            for(IOFSwitch x : switches) {
                d.put(x, Integer.MAX_VALUE - 1);
                q.put(x, Integer.MAX_VALUE - 1);
                parent.put(x, null);
            }

            d.put(v, 0);
            q.put(v, 0);

            for(Link link : getLinks()) {
                IOFSwitch source = getSwitches().get(link.getSrc());
                IOFSwitch dest = getSwitches().get(link.getDst());

                if(source == v) {
                    d.put(dest, 1);
                    q.put(dest, 1);
                    parent.put(dest, source);
                }
            }

            Set<IOFSwitch> s = new HashSet<IOFSwitch>();
            IOFSwitch u;

            while(!q.isEmpty()) {
                u = getMinCostSwitch(q);
                q.remove(u);
                s.add(u);
                for(Link link : getLinks()) {
                    IOFSwitch source = getSwitches().get(link.getSrc());
                    IOFSwitch adj = getSwitches().get(link.getDst());

                    if(source == u && !s.contains(adj)) {
                        if(d.get(u) + 1 < d.get(adj)) {
                            d.put(adj, d.get(u) + 1);
                            q.put(adj, d.get(u) + 1);
                            parent.put(adj, u);
                        }

                    }
                }
            }

            shortestPaths.put(v, parent);
        }

        return shortestPaths;
    }

    private IOFSwitch getMinCostSwitch(HashMap<IOFSwitch, Integer> q) {

        Integer min = Integer.MAX_VALUE - 1;
        IOFSwitch ans = null;

        for(IOFSwitch key : q.keySet()) {
            if(q.get(key) <= min) {
                min = q.get(key);
                ans = key;
            }
        }

        return ans;
    }
}

