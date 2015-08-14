/**
 * Copyright (c) 2012 Todoroo Inc
 *
 * See the file "LICENSE" for the full license governing this code.
 */
package com.todoroo.astrid.gtasks.sync;

import android.content.Context;
import android.text.TextUtils;

import com.google.api.services.tasks.model.TaskLists;
import com.google.api.services.tasks.model.Tasks;
import com.todoroo.andlib.data.AbstractModel;
import com.todoroo.andlib.data.TodorooCursor;
import com.todoroo.andlib.sql.Criterion;
import com.todoroo.andlib.sql.Join;
import com.todoroo.andlib.sql.Query;
import com.todoroo.andlib.utility.DateUtilities;
import com.todoroo.astrid.dao.MetadataDao.MetadataCriteria;
import com.todoroo.astrid.dao.StoreObjectDao;
import com.todoroo.astrid.dao.TaskDao;
import com.todoroo.astrid.data.Metadata;
import com.todoroo.astrid.data.SyncFlags;
import com.todoroo.astrid.data.Task;
import com.todoroo.astrid.gtasks.GtasksList;
import com.todoroo.astrid.gtasks.GtasksListService;
import com.todoroo.astrid.gtasks.GtasksMetadata;
import com.todoroo.astrid.gtasks.GtasksMetadataService;
import com.todoroo.astrid.gtasks.GtasksPreferenceService;
import com.todoroo.astrid.gtasks.GtasksTaskListUpdater;
import com.todoroo.astrid.gtasks.api.GoogleTasksException;
import com.todoroo.astrid.gtasks.api.GtasksInvoker;
import com.todoroo.astrid.gtasks.auth.GtasksTokenValidator;
import com.todoroo.astrid.service.TaskService;
import com.todoroo.astrid.sync.SyncResultCallback;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tasks.R;
import org.tasks.injection.ForApplication;
import org.tasks.preferences.Preferences;
import org.tasks.sync.SyncExecutor;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;
import javax.inject.Singleton;

import static org.tasks.date.DateTimeUtils.newDateTime;

@Singleton
public class GtasksSyncV2Provider {

    public class SyncExceptionHandler {
        public void handleException(String tag, Exception e) {
            log.error("{}: {}", tag, e.getMessage(), e);
        }
    }

    private final SyncExceptionHandler handler = new SyncExceptionHandler();

    private void finishSync(SyncResultCallback callback) {
        gtasksPreferenceService.recordSuccessfulSync();
        callback.finished();
    }

    @Override
    public String toString() {
        return getName();
    }

    private static final Logger log = LoggerFactory.getLogger(GtasksSyncV2Provider.class);

    private final TaskService taskService;
    private final StoreObjectDao storeObjectDao;
    private final GtasksPreferenceService gtasksPreferenceService;
    private final GtasksSyncService gtasksSyncService;
    private final GtasksListService gtasksListService;
    private final GtasksMetadataService gtasksMetadataService;
    private final GtasksTaskListUpdater gtasksTaskListUpdater;
    private final Context context;
    private final Preferences preferences;
    private final GtasksTokenValidator gtasksTokenValidator;
    private final GtasksMetadata gtasksMetadataFactory;
    private final SyncExecutor executor;

    @Inject
    public GtasksSyncV2Provider(TaskService taskService, StoreObjectDao storeObjectDao, GtasksPreferenceService gtasksPreferenceService,
                                GtasksSyncService gtasksSyncService, GtasksListService gtasksListService, GtasksMetadataService gtasksMetadataService,
                                GtasksTaskListUpdater gtasksTaskListUpdater, @ForApplication Context context, Preferences preferences,
                                GtasksTokenValidator gtasksTokenValidator, GtasksMetadata gtasksMetadata, SyncExecutor executor) {
        this.taskService = taskService;
        this.storeObjectDao = storeObjectDao;
        this.gtasksPreferenceService = gtasksPreferenceService;
        this.gtasksSyncService = gtasksSyncService;
        this.gtasksListService = gtasksListService;
        this.gtasksMetadataService = gtasksMetadataService;
        this.gtasksTaskListUpdater = gtasksTaskListUpdater;
        this.context = context;
        this.preferences = preferences;
        this.gtasksTokenValidator = gtasksTokenValidator;
        this.gtasksMetadataFactory = gtasksMetadata;
        this.executor = executor;
    }

    private String getName() {
        return context.getString(R.string.gtasks_GPr_header);
    }

    public void signOut() {
        gtasksPreferenceService.clearLastSyncDate();
        gtasksPreferenceService.setToken(null);
        gtasksPreferenceService.setUserName(null);
        gtasksMetadataService.clearMetadata();
    }

    public boolean isActive() {
        return gtasksPreferenceService.isLoggedIn();
    }

    public void synchronizeActiveTasks(final SyncResultCallback callback) {
        executor.execute(callback, new Runnable() {
            @Override
            public void run() {
                callback.started();

                try {
                    String authToken = getValidatedAuthToken();
                    final GtasksInvoker invoker = new GtasksInvoker(context, gtasksTokenValidator, authToken);
                    TaskLists remoteLists = null;
                    try {
                        remoteLists = invoker.allGtaskLists();
                        gtasksListService.updateLists(remoteLists);
                    } catch (IOException e) {
                        handler.handleException("gtasks-sync=io", e); //$NON-NLS-1$
                    }

                    if (remoteLists == null) {
                        finishSync(callback);
                        return;
                    }

                    List<GtasksList> listsToUpdate = gtasksListService.getListsToUpdate(remoteLists);

                    if (listsToUpdate.isEmpty()) {
                        finishSync(callback);
                        return;
                    }

                    final AtomicInteger finisher = new AtomicInteger(listsToUpdate.size());

                    for (final GtasksList list : listsToUpdate) {
                        executor.execute(callback, new Runnable() {
                            @Override
                            public void run() {
                                synchronizeListHelper(list, invoker, handler);
                                if (finisher.decrementAndGet() == 0) {
                                    pushUpdated(invoker);
                                    finishSync(callback);
                                }
                            }
                        });
                    }
                } catch(Exception e) {
                    handler.handleException("gtasks-sync=io", e); //$NON-NLS-1$
                    callback.finished();
                }
            }
        });
    }

    private synchronized void pushUpdated(GtasksInvoker invoker) {
        TodorooCursor<Task> queued = taskService.query(Query.select(Task.PROPERTIES).
                join(Join.left(Metadata.TABLE, Criterion.and(MetadataCriteria.withKey(GtasksMetadata.METADATA_KEY), Task.ID.eq(Metadata.TASK)))).where(
                        Criterion.or(Task.MODIFICATION_DATE.gt(GtasksMetadata.LAST_SYNC), Metadata.KEY.isNull())));
        pushTasks(queued, invoker);
    }

    private synchronized void pushTasks(TodorooCursor<Task> queued, GtasksInvoker invoker) {
        try {
            for (queued.moveToFirst(); !queued.isAfterLast(); queued.moveToNext()) {
                Task task = new Task(queued);
                try {
                    gtasksSyncService.pushTaskOnSave(task, task.getMergedValues(), invoker);
                } catch (IOException e) {
                    handler.handleException("gtasks-sync-io", e); //$NON-NLS-1$
                }
            }
        } finally {
            queued.close();
        }
    }

    public void synchronizeList(final GtasksList gtasksList, final SyncResultCallback callback) {
        executor.execute(callback, new Runnable() {
            @Override
            public void run() {
                callback.started();

                try {
                    String authToken = getValidatedAuthToken();
                    gtasksSyncService.waitUntilEmpty();
                    final GtasksInvoker service = new GtasksInvoker(context, gtasksTokenValidator, authToken);
                    synchronizeListHelper(gtasksList, service, null);
                } finally {
                    callback.finished();
                }
            }
        });
    }

    private String getValidatedAuthToken() {
        String authToken = gtasksPreferenceService.getToken();
        try {
            authToken = gtasksTokenValidator.validateAuthToken(context, authToken);
            if (authToken != null) {
                gtasksPreferenceService.setToken(authToken);
            }
        } catch (GoogleTasksException e) {
            log.error(e.getMessage(), e);
            authToken = null;
        }
        return authToken;
    }

    private synchronized void synchronizeListHelper(GtasksList list, GtasksInvoker invoker,
            SyncExceptionHandler errorHandler) {
        String listId = list.getRemoteId();
        long lastSyncDate = list.getLastSync();

        /**
         * Find tasks which have been associated with the list internally, but have not yet been
         * pushed to Google Tasks (and so haven't yet got a valid ID).
         */
        Criterion not_pushed_tasks = Criterion.and(
                Metadata.KEY.eq(GtasksMetadata.METADATA_KEY),
                GtasksMetadata.LIST_ID.eq(listId),
                GtasksMetadata.ID.eq("")
        );
        TodorooCursor<Task> qs = taskService.query(Query.select(Task.PROPERTIES).
                join(Join.left(Metadata.TABLE, Criterion.and(MetadataCriteria.withKey(GtasksMetadata.METADATA_KEY), Task.ID.eq(Metadata.TASK)))).where(not_pushed_tasks)
        );
        pushTasks(qs, invoker);

        boolean includeDeletedAndHidden = lastSyncDate != 0;
        try {
            Tasks taskList = invoker.getAllGtasksFromListId(listId, includeDeletedAndHidden,
                    includeDeletedAndHidden, lastSyncDate + 1000L);
            List<com.google.api.services.tasks.model.Task> tasks = taskList.getItems();
            if (tasks != null) {
                for (com.google.api.services.tasks.model.Task t : tasks) {
                    GtasksTaskContainer container = new GtasksTaskContainer(t, listId, gtasksMetadataFactory.createEmptyMetadata(AbstractModel.NO_ID));
                    gtasksMetadataService.findLocalMatch(container);
                    container.gtaskMetadata.setValue(GtasksMetadata.GTASKS_ORDER, Long.parseLong(t.getPosition()));
                    container.gtaskMetadata.setValue(GtasksMetadata.PARENT_TASK, gtasksMetadataService.localIdForGtasksId(t.getParent()));
                    container.gtaskMetadata.setValue(GtasksMetadata.LAST_SYNC, DateUtilities.now() + 1000L);
                    write(container);
                    lastSyncDate = Math.max(lastSyncDate, container.getUpdateTime());
                }
                list.setLastSync(lastSyncDate);
                storeObjectDao.persist(list);
                gtasksTaskListUpdater.correctOrderAndIndentForList(listId);
            }
        } catch (IOException e) {
            if (errorHandler != null) {
                errorHandler.handleException("gtasks-sync-io", e); //$NON-NLS-1$
            } else {
                log.error(e.getMessage(), e);
            }
        }
    }

    private void write(GtasksTaskContainer task) {
        //  merge astrid dates with google dates

        if(task.task.isSaved()) {
            Task local = taskService.fetchById(task.task.getId(), Task.DUE_DATE, Task.COMPLETION_DATE);
            if (local == null) {
                task.task.clearValue(Task.ID);
                task.task.clearValue(Task.UUID);
            } else {
                mergeDates(task.task, local);
            }
        } else { // Set default importance and reminders for remotely created tasks
            task.task.setImportance(preferences.getIntegerFromString(
                    R.string.p_default_importance_key, Task.IMPORTANCE_SHOULD_DO));
            TaskDao.setDefaultReminders(preferences, task.task);
        }
        if (!TextUtils.isEmpty(task.task.getTitle())) {
            task.task.putTransitory(SyncFlags.GTASKS_SUPPRESS_SYNC, true);
            gtasksMetadataService.saveTaskAndMetadata(task);
        }
    }

    private void mergeDates(Task remote, Task local) {
        if(remote.hasDueDate() && local.hasDueTime()) {
            DateTime oldDate = newDateTime(local.getDueDate());
            DateTime newDate = newDateTime(remote.getDueDate())
                    .withHourOfDay(oldDate.getHourOfDay())
                    .withMinuteOfHour(oldDate.getMinuteOfHour())
                    .withSecondOfMinute(oldDate.getSecondOfMinute());
            long setDate = Task.createDueDate(Task.URGENCY_SPECIFIC_DAY_TIME,
                    newDate.getMillis());
            remote.setDueDate(setDate);
        }
    }
}
