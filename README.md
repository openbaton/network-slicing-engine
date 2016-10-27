  <img src="https://raw.githubusercontent.com/openbaton/openbaton.github.io/master/images/openBaton.png" width="250"/>
  
  Copyright © 2015-2016 [Open Baton](http://openbaton.org). 
  Licensed under [Apache v2 License](http://www.apache.org/licenses/LICENSE-2.0).

# Network Slicing Engine
This ensures QoS configuration defined in the Descriptors provided by the NFVO.

# Technical Requirements
This section covers the requirements that must be met by the environment in order to satisfy the demands of the network-slicing-engine:

* installed and configured Open Baton NFVO/gVNFM (>=2.1.3)
* installed and configured Openstack (>=Mitaka)

# How to install Network Slicing Engine

Quick installation process:
```
$ mkdir /opt/openbaton
$ cd /opt/openbaton
$ git clone https://github.com/openbaton/network-slicing-engine
$ cd network-slicing-engine
$ ./network-slicing-engine.sh compile init
$ # then configure the properties file to suit your setup in regards of nfvo and rabbitmq
$ $EDITOR /etc/openbaton/network-slicing-engine.properties
$ ./network-slicing-engine.sh start
$ # to attach and see the logging output
$ screen -x openbaton
```

# How to use Network Slicing Engine
The currently only supported driver is neutron, which will use the native QoS capabilities of Openstack. To use it simply set ```networkslicingengine.driver=neutron``` in the configuration file. To set QoS policies in your NSD specify the following QoS parameter in the virtual_link of your vnfd configuration. 

```
  "virtual_link":[
    {
      "name":"NAME_OF_THE_NETWORK",
      "qos":[
        "minimum_bandwith:BRONZE"
      ]
    }
  ]
```
# How to extend Network Slicing Engine

# Issue tracker

Issues and bug reports should be posted to the GitHub Issue Tracker of this project

# What is Open Baton?

Open Baton is an open source project providing a comprehensive implementation of the ETSI Management and Orchestration (MANO) specification and the TOSCA Standard.

Open Baton provides multiple mechanisms for interoperating with different VNFM vendor solutions. It has a modular archiecture which can be easily extended for supporting additional use cases. 

It integrates with OpenStack as standard de-facto VIM implementation, and provides a driver mechanism for supporting additional VIM types. It supports Network Service management either using the provided Generic VNFM and Juju VNFM, or integrating additional specific VNFMs. It provides several mechanisms (REST or PUB/SUB) for interoperating with external VNFMs. 

It can be combined with additional components (Monitoring, Fault Management, Autoscaling, and Network Slicing Engine) for building a unique MANO comprehensive solution.

# Source Code and documentation

The Source Code of the other Open Baton projects can be found [here][openbaton-github] and the documentation can be found [here][openbaton-doc] .

# News and Website

Check the [Open Baton Website][openbaton]
Follow us on Twitter @[openbaton][openbaton-twitter].

# Licensing and distribution
Copyright [2015-2016] Open Baton project

Licensed under the Apache License, Version 2.0 (the "License");

you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

# Support
The Open Baton project provides community support through the Open Baton Public Mailing List and through StackOverflow using the tags openbaton.

# Supported by
  <img src="https://raw.githubusercontent.com/openbaton/openbaton.github.io/master/images/fokus.png" width="250"/><img src="https://raw.githubusercontent.com/openbaton/openbaton.github.io/master/images/tu.png" width="150"/>

[fokus-logo]: https://raw.githubusercontent.com/openbaton/openbaton.github.io/master/images/fokus.png
[openbaton]: http://openbaton.org
[openbaton-doc]: http://openbaton.org/documentation
[openbaton-github]: http://github.org/openbaton
[openbaton-logo]: https://raw.githubusercontent.com/openbaton/openbaton.github.io/master/images/openBaton.png
[openbaton-mail]: mailto:users@openbaton.org
[openbaton-twitter]: https://twitter.com/openbaton
[tub-logo]: https://raw.githubusercontent.com/openbaton/openbaton.github.io/master/images/tu.png
