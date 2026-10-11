package de.open4me.hibiscus.reports.mcp;

import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public final class McpHttpServerTests
{
    private McpHttpServerTests()
    {
    }

    public static void run()
    {
        syncWorkersInheritApplicationClassLoader();
    }

    private static void syncWorkersInheritApplicationClassLoader()
    {
        Thread caller = Thread.currentThread();
        ClassLoader original = caller.getContextClassLoader();
        ClassLoader applicationLoader = new ClassLoader(ClassLoader.getSystemClassLoader()) {};
        try
        {
            caller.setContextClassLoader(ClassLoader.getSystemClassLoader());
            FutureTask<ClassLoader[]> task = new FutureTask<>(() -> {
                ClassLoader workerLoader = Thread.currentThread().getContextClassLoader();
                FutureTask<ClassLoader> syncTask = new FutureTask<>(
                    () -> Thread.currentThread().getContextClassLoader());
                Thread syncWorker = new Thread(syncTask, "test-sync-worker");
                syncWorker.setDaemon(true);
                syncWorker.start();
                return new ClassLoader[] {workerLoader, syncTask.get(5, TimeUnit.SECONDS)};
            });
            Thread worker = McpHttpServer.createWorker(task, applicationLoader);
            worker.start();
            ClassLoader[] loaders = task.get(5, TimeUnit.SECONDS);
            if (loaders[0] != applicationLoader)
                throw new AssertionError("MCP worker must use the application classloader");
            if (loaders[1] != applicationLoader)
                throw new AssertionError("Sync worker must inherit the application classloader");
            if (caller.getContextClassLoader() != ClassLoader.getSystemClassLoader())
                throw new AssertionError("Creating a worker must preserve the caller's classloader");
        }
        catch (InterruptedException e)
        {
            caller.interrupt();
            throw new AssertionError("Classloader test interrupted", e);
        }
        catch (Exception e)
        {
            throw new AssertionError("Classloader test failed", e);
        }
        finally
        {
            caller.setContextClassLoader(original);
        }
    }
}
