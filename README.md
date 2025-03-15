# Birdy LB

A lightweight, high-performance, and agile load balancer designed for cloud-agnostic, extensible deployments.

- **High Performance** – Efficient request routing with minimal latency.
- **Cloud-Agnostic** – Works on bare-metal, hybrid, and edge environments.
- **Extensible & Modular** – Customizable algorithms and integrations.
- **Zero-Downtime Scaling** – Seamlessly add/remove nodes without disruption.

[![Build and Test Application](https://github.com/ranzyblessings/birdy-lb/actions/workflows/build-and-test.yaml/badge.svg)](https://github.com/ranzyblessings/birdy-lb/actions/workflows/build-and-test.yaml)
[![Deploy Docker Image](https://github.com/ranzyblessings/birdy-lb/actions/workflows/deploy-docker-image.yaml/badge.svg)](https://github.com/ranzyblessings/birdy-lb/actions/workflows/deploy-docker-image.yaml)

---

## Features

- **Backend Health Awareness and Dynamic Discovery:** Integration with service registries like Consul or Kubernetes DNS.
- **Resilience:** Retry mechanisms and circuit-breaking for failed backends.
- **Observability:** Metrics and logging tailored to the strategy’s decisions.
- **Extensibility:** Pluggable advanced algorithms (e.g., least connections, latency-based).

---

## Requirements

Ensure that the following tools are installed:

- [Docker v27.5.1](https://www.docker.com/get-started) (or the latest version)
- [Java 23](https://docs.aws.amazon.com/corretto/latest/corretto-23-ug/downloads-list.html) (or the latest version)

---

## How To Contribute

We welcome contributions from developers of all skill levels! Here’s how you can get started:

1. **Fork the Repository:** Create a personal copy of the repo.
2. **Explore Issues:** Check the [issue tracker](https://github.com/ranzyblessings/birdy-lb/issues) for open
   issues or feature requests.
3. **Create a Branch:** Work on your feature or bug fix in a separate branch.
4. **Submit a Pull Request:** Once **ready and tests are passing**, submit a PR for review.

### Areas to Contribute

- **Feature Development:** Develop advanced load balancing features, including dynamic backend discovery, resilience
  mechanisms, and customizable routing algorithms.
- **Bug Fixes:** Identify and resolve issues.
- **Documentation:** Improve or expand the existing documentation.
- **Testing:** Write unit and integration tests to validate functionality and ensure system reliability.

---

## License

This project is open-source software licensed under
the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).