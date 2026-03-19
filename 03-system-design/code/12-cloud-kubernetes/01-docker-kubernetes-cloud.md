# Docker, Kubernetes, and Cloud Runtime Basics

This module covers the deployment and platform concepts strong backend engineers are expected to reason about in practice.

## Topics to know

- container image layering and why image size matters
- Dockerfile basics: deterministic builds, non-root user, small base image
- Kubernetes primitives: Deployment, Service, ConfigMap, Secret, Ingress, HPA
- readiness vs liveness probes
- horizontal autoscaling signals
- service discovery and internal networking
- stateless services vs stateful data systems
- managed cloud services trade-offs

## Readiness vs liveness

- readiness decides whether the pod should receive traffic
- liveness decides whether the process should be restarted
- a dependency outage often should fail readiness, not liveness

## Production habits

- use immutable image tags in deployment configs
- keep containers stateless where possible
- separate config and secrets from the image
- set CPU and memory requests and limits deliberately
- prefer rolling deploys with observability gates

## Managed service trade-offs

Use managed services when:
- operational burden is not part of product differentiation
- team size is small relative to platform scope
- reliability of the managed option is good enough

Build or self-host when:
- control or cost structure is a major constraint
- you need behaviors the managed service cannot provide
- regulatory or data locality requirements force it
