# Linux and Process Basics for Backend Engineers

Use this module to build the OS intuition interviewers expect when discussing production incidents, CPU saturation, file descriptor leaks, or stuck Java processes.

## What to know deeply

- process vs thread
- virtual memory, RSS, heap, stack, page cache
- file descriptors and socket lifecycle
- context switches and why too many threads hurt latency
- signals: `SIGTERM`, `SIGKILL`, graceful shutdown
- standard Linux debugging tools: `top`, `htop`, `ps`, `lsof`, `vmstat`, `iostat`, `netstat` or `ss`

## Interview-level mental models

- A process is the OS container for memory, file descriptors, and threads.
- A thread is a schedulable execution unit inside a process.
- If a service is slow, first decide whether it is CPU-bound, IO-bound, lock-bound, or memory-bound.
- A high thread count can increase latency through context switching even when CPU usage is not 100%.
- File descriptor exhaustion can break outbound calls, DB connections, and inbound traffic at the same time.

## Common backend incidents

### 1. CPU saturation
Symptoms:
- high request latency
- elevated run queue
- one or more hot methods in profiler output

Check:
- `top -H -p <pid>`
- thread dump correlation
- allocation rate and serialization hot spots

### 2. Memory pressure
Symptoms:
- rising GC pauses
- OOM kill from container or host
- swap activity or reclaim pressure

Check:
- heap trend
- off-heap buffers
- page cache pressure
- `vmstat 1`

### 3. File descriptor leak
Symptoms:
- socket creation failures
- `Too many open files`
- random downstream connectivity failures

Check:
- `lsof -p <pid> | wc -l`
- connection pool configuration
- response body or stream closure paths

## Socket lifecycle you should know

1. client opens TCP connection
2. server accepts socket and allocates a file descriptor
3. reads and writes happen through that descriptor
4. connection closes gracefully with FIN or abruptly with RST
5. closed sockets may remain in `TIME_WAIT`

Important:
- keep-alive reduces connection setup cost
- too many short-lived connections increase handshake overhead and ephemeral port pressure

## Commands worth knowing

```bash
ps -ef | grep java
lsof -p 12345 | head
ss -tanp | grep 8080
vmstat 1
iostat -xz 1
top -H -p 12345
```

## Interview questions you should handle

- Why can a Java service be slow even when CPU usage looks moderate?
- What does page cache do for a backend service?
- How would you debug a file descriptor leak in production?
- Why is `SIGTERM` preferred over `SIGKILL` for shutdown?

## Practice checklist

- explain CPU-bound vs IO-bound bottlenecks without hand-waving
- explain how thread pools relate to OS scheduling
- explain what happens when a process runs out of file descriptors
- explain why memory usage inside containers needs limits and observability
