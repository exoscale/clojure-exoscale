# The Exoscale Clojure library

A library providing low-level and high-level constructs
to interact with [Exoscale](https://exoscale.com)
infrastructure resources.

## Supported Resources

- 🗹 Virtual machine instances
- 🗹 Virtual machine templates
- 🗹 SSH keypairs
- ☐ Elastic IP Addresses
- ☐ Security Groups
- ☐ Private Networks
- ☐ Object Storage
- ☐ DNS
- ☐ Status page management

## Configuration

This library needs access to your Exoscale credentials to
perform operations.

Credentials can be provided in several ways.

### The configuration map

Most functions take a **config** map as their first argument.
This map has two mandatory keys and an optional one.

```clojure
{:api-key    "EXO..."
 :api-secret "..."
 :endpoint   "https://api.exoscale.com/compute"} ;; optional
```

### Configuration file

Most library consumers will want to read configuration from
a file to avoid wiring credentials directly in their application.

The `exoscale.compute.api.config/from-environment` function will
load credentials in the following way:

- Read either `$XDG_CONFIG_HOME/exoscale/exoscale.toml` or `$XDG_CONFIG_HOME/exoscale/exoscale.edn`, expecting the same structure than in the [Exoscale CLI](https://exoscale.github.io/cli)
- Look-up the default account or a provided override
- Process the following environment overrides: `EXOSCALE_ACCOUNT`, `EXOSCALE_API_KEY`, `EXOSCALE_API_SECRET`, `EXOSCALE_ENDPOINT`.





