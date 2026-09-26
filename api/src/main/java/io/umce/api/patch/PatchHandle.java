package io.umce.api.patch;

/** Reversible result of activating a patch. Close must release resources and undo hooks. */
public interface PatchHandle extends AutoCloseable {
    @Override
    void close() throws Exception;
}
