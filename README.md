  <img src="https://raw.githubusercontent.com/openbaton/openbaton.github.io/master/images/openBaton.png" width="250"/>
  
  Copyright © 2015-2016 [Open Baton](http://openbaton.org). 
  Licensed under [Apache v2 License](http://www.apache.org/licenses/LICENSE-2.0).

# Network Slicing Engine

This external component provides a Network Slicing Engine (NSE). In the following fundamentals are described such as installing the NSE, configuring it and how to configure Network Slicing requirements.

In a nutshell this component ensures QoS configuration defined in the Descriptors provided by the NFVO.

The `network-slicing-engine` is implemented in java using the [spring.io] framework. It runs as an external component and communicates with the NFVO via Open Baton's SDK.

Additionally, the NSE uses the a plugin mechanism to allow whatever driver is needed to setup QoS. Currently, it supports only the neutron driver which allows to configure QoS in OpenStack directly. Hence, the NSE requires at least version Mitaka of OpenStack since it was recently introduced.

Before starting this component you have to do the configuration of the NSE that is described in the [next chapter](#manual-configuration-of-the-network-slicing-engine) followed by the guide of [how to start](#starting-the-network-slicing-engine) and [how to use](#how-to-use-the-network-slicing-engine) it.

# Technical Requirements
This section covers the requirements that must be met by the environment in order to satisfy the demands of the NSE:

* Installed and configured Open Baton NFVO/gVNFM (>=5.0.0)
* OpenStack installation including Neutron QoS APIs (https://docs.openstack.org/mitaka/networking-guide/config-qos.html)

# How to install Network Slicing Engine
Different options are available for the installation of the NSE. Either you use the fully automated bootstrap where all configurations are done automatically where you can choose between the installation based on the debian package or on the source code which is suggested for development. Apart from the bootstrap you can also use the debian or the source code installation where you need to configure the NSE manually. 

## Installation via bootstrap

Using the bootstrap gives a fully automated standalone installation of the NS including installation and configuration.

The only thing to do is to execute the following command and follow the configuration process: 

```bash
bash <(curl -fsSkl https://raw.githubusercontent.com/openbaton/network-slicing-engine/master/bootstrap)
```

Once you started the bootstrap you can choose between different options, such as installing this component via debian packages or from the source code (mainly for development)

## Installation via debian package

When using the debian package you need to add the apt-repository of Open Baton to your local environment with the following command if not yet done:
 
```bash
wget -O - http://get.openbaton.org/keys/public.gpg.key | apt-key add -
echo "deb http://get.openbaton.org/repos/apt/debian/ stable main" >> /etc/apt/sources.list
```

Once you added the repo to your environment you should update the list of repos by executing:

```bash
apt-get update
```

Now you can install the NSE by executing:

```bash
apt-get install openbaton-nse
```

## Installation from the source code

The latest stable version NSE can be cloned from this [repository][nse-repo] by executing the following command:

```bash
git clone https://github.com/openbaton/network-slicing-engine.git
```

Once this is done, go inside the cloned folder and make use of the provided script to compile the project as done below:

```bash
./network-slicing-engine.sh compile
```

# Manual configuration of the Network Slicing Engine

This chapter describes what needs to be done before starting the Network Slicing Engine. This includes the configuration file and properties, and also how to define QoS requirements in the descriptor.

## Configuration file
The configuration file must be copied to `/etc/openbaton/openbaton-nse.properties` by executing the following command from inside the repository folder:

```bash
cp src/main/resources/application.properties /etc/openbaton/openbaton-nse.properties
```

If done, check out the following chapter in order to understand the configuration parameters.

## Configuration properties

This chapter describes the parameters that must be considered for configuring the Network Slicing Engine.

| Params          				| Meaning       																|
| -------------   				| -------------																|
| logging.file					| location of the logging file |
| logging.level.*               | logging levels of the defined modules  |
| rabbitmq.host                 | host of RabbitMQ |
| rabbitmq.username             | username for authorizing towards RabbitMQ |
| rabbitmq.password             | password for authorizing towards RabbitMQ |
| nfvo.ip                       | IP of the NFVO |
| nfvo.port                     | Port of the NFVO |
| nfvo.username                 | username for authorizing towards NFVO |
| nfvo.password                 | password for authorizing towards NFVO |
| nfvo.project.name             | project used for registering for the events|
| nse.service.key               | Service Key obtained when registering the `network-slicing-engine` service via the NFVO|

# Starting the Network Slicing Engine

How to start the NSE depends of the way you installed this component.

### Debian packages

If you installed the NSE with the debian packages you can start it with the following command:

```bash
openbaton-nse start
```

For stopping it you can just type:

```bash
openbaton-nse stop
```

### Source code

If you are using the source code you can start the NSE  easily by using the provided script with the following command:

```bash
./network-slicing-engine.sh start
```

Once the NSE is started, you can access the screen session by executing:

```bash
screen -r openbaton
```

For stopping you can use:
```bash
./network-slicing-engine.sh kill
```

**Note** Since the NSE subscribes to specific events towards the NFVO, you should take care about that the NFVO is already running when starting the NSE.

# How to use Network Slicing Engine
The currently only supported NFVI is OpenStack Neutron, which will use the native QoS capabilities of Openstack Mitaka. To set QoS policies in your NSD specify the following QoS parameter in the virtual_link of your vnfd configuration.

```
  "virtual_link":[
    {
      "name":"NAME_OF_THE_NETWORK",
      "qos":[
        "maximum_bandwidth:BRONZE"
      ]
    }
  ]
```

# Issue tracker

Issues and bug reports should be posted to the GitHub Issue Tracker of this project

# What is Open Baton?

Open Baton is an open source project providing a comprehensive implementation of the ETSI Management and Orchestration (MANO) specification and the TOSCA Standard.

Open Baton provides multiple mechanisms for interoperating with different VNFM vendor solutions. It has a modular architecture which can be easily extended for supporting additional use cases. 

It integrates with OpenStack as standard de-facto VIM implementation, and provides a driver mechanism for supporting additional VIM types. It supports Network Service management either using the provided Generic VNFM and Juju VNFM, or integrating additional specific VNFMs. It provides several mechanisms (REST or PUB/SUB) for interoperating with external VNFMs. 

It can be combined with additional components (Monitoring, Fault Management, Autoscaling, and Network Slicing Engine) for building a unique MANO comprehensive solution.

## Source Code and documentation

The Source Code of the other Open Baton projects can be found [here][openbaton-github] and the documentation can be found [here][openbaton-doc]

## News and Website

Check the [Open Baton Website][openbaton]

Follow us on Twitter @[openbaton][openbaton-twitter]

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
[nse-repo]: https://github.com/openbaton/network-slicing
[spring.io]:https://spring.io/
