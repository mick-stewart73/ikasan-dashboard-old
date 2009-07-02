/*
 * $Id$
 * $URL$
 * 
 * ====================================================================
 * Ikasan Enterprise Integration Platform
 * Copyright (c) 2003-2008 Mizuho International plc. and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the 
 * Free Software Foundation Europe e.V. Talstrasse 110, 40217 Dusseldorf, Germany 
 * or see the FSF site: http://www.fsfeurope.org/.
 * ====================================================================
 */
package org.ikasan.framework.initiator.scheduled.quartz;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import javax.resource.ResourceException;

import junit.framework.Assert;

import org.ikasan.framework.component.Event;
import org.ikasan.framework.component.IkasanExceptionHandler;
import org.ikasan.framework.event.service.EventProvider;
import org.ikasan.framework.exception.IkasanExceptionAction;
import org.ikasan.framework.exception.IkasanExceptionActionImpl;
import org.ikasan.framework.exception.IkasanExceptionActionType;
import org.ikasan.framework.flow.Flow;
import org.ikasan.framework.flow.FlowInvocationContext;
import org.ikasan.framework.initiator.AbortTransactionException;
import org.ikasan.framework.monitor.MonitorListener;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.After;
import org.junit.Test;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;

/**
 * This test class supports the <code>QuartzStatefulScheduledDrivenInitiator</code> class.
 * 
 * @author Ikasan Development Team
 */
public class QuartzStatefulScheduledDrivenInitiatorTest
{
    /**
     * Mockery for mocking concrete classes
     */
    private Mockery classMockery = new Mockery()
    {
        {
            setImposteriser(ClassImposteriser.INSTANCE);
        }
    };

    /**
     * Mock objects
     */
    final EventProvider eventProvider = classMockery
            .mock(EventProvider.class);
    final Event event = classMockery.mock(Event.class);
    final Flow flow = classMockery.mock(Flow.class);
    final IkasanExceptionHandler exceptionHandler = 
        classMockery.mock(IkasanExceptionHandler.class);
    final Scheduler scheduler = classMockery.mock(Scheduler.class);

    final MonitorListener monitorListener = classMockery.mock(MonitorListener.class);

    /**
     * Real objects
     */
    final IkasanExceptionAction rollbackRetryAction =
        new IkasanExceptionActionImpl(IkasanExceptionActionType.ROLLBACK_RETRY);
    final IkasanExceptionAction rollbackStopAction =
        new IkasanExceptionActionImpl(IkasanExceptionActionType.ROLLBACK_STOP);
    
    final String initiatorName = "initiatorName";
    final String moduleName = "moduleName";




    /**
     * Test execution of the QuartzStatefulScheduledDrivenInitiator based on a
     * failure due to initiator being in a 'STOPPED' state.
     * 
     * @throws SchedulerException
     */
    public void test_failed_InvokeDueToInitiatorNotBeingInAStartedState()
        throws SchedulerException
    {

        //
        // expectations due to stop() being called as part of the test set-up
        setStopInitiatorExpectations();
        
        //
        // run test
        QuartzStatefulScheduledDrivenInitiator sdi = new QuartzStatefulScheduledDrivenInitiator(
                initiatorName, moduleName,eventProvider, flow, exceptionHandler);

        // give the initiator a quartz scheduler
        sdi.setScheduler(scheduler);

        // check initiator status is running (default state on instantiation)
        assertTrue(sdi.getState().isRunning());

        // stop initiator and check state is stopped
        sdi.stop();
        assertTrue(sdi.getState().isStopped());

        // invoke initiator
        sdi.invoke();
    }


    
    /**
     * Test execution of the QuartzStatefulScheduledDrivenInitiator based on a
     * successful initiator execute callback, but no returned event.
     * 
     * @throws ResourceException
     */
    @Test
    public void test_successful_ExecuteWithNoEvent() 
        throws ResourceException
    {
        QuartzStatefulScheduledDrivenInitiator sdi = setupInitiator();
        
        // set expectations
        classMockery.checking(new Expectations()
        {
            {
                // get events from event provider
                one(eventProvider).getEvents();
                will(returnValue(null));
            }
        });

        //expectations for handleNullAction
        setExpectationsForResume(false);

        // invoke initiator
        sdi.invoke();
    }

    
    /**
     * Test execution of the QuartzStatefulScheduledDrivenInitiator based on a
     * successful initiator execute callback with returned event.
     * 
     * @throws ResourceException
     */
    @Test
    public void test_successful_ExecuteWithEvent() 
        throws ResourceException
    {
        QuartzStatefulScheduledDrivenInitiator sdi = setupInitiator();
        
        // set expectations
        classMockery.checking(new Expectations()
        {
            {
                // return 'null' ikasanExceptionAction from the flow invocation
                one(flow).invoke((FlowInvocationContext)with(a(FlowInvocationContext.class)), with(any(Event.class)));
                will(returnValue(null));
            }
        });

        // set common expectations
        this.setEventExpectations();
        
        //for a successful execution we will be handling a null action
        setExpectationsForResume(false);

        // invoke initiator
        sdi.invoke();
    }



    /**
     * Test execution of the QuartzStatefulScheduledDrivenInitiator based on a
     * successful initiator execute callback with returned multiple events.
     * 
     * @throws ResourceException
     */
    @Test
    public void test_successful_ExecuteWithMultipleEvents() 
        throws ResourceException
    {
        QuartzStatefulScheduledDrivenInitiator sdi = setupInitiator();       
        
        final int numOfEvents = 3;

        // 
        // set expectations
        classMockery.checking(new Expectations()
        {
            {
                // return 'null' ikasanExceptionAction from the flow invocation
                exactly(numOfEvents).of(flow).invoke((FlowInvocationContext)with(a(FlowInvocationContext.class)), with(any(Event.class)));
                will(returnValue(null));
            }
        });

        
        // set common expectations
        this.setMultipleEventExpectations(numOfEvents);       
        
        //for a successful execution we will be handling a null action
        setExpectationsForResume(false);
        
        // invoke initiator
        sdi.invoke();
    }

    /**
     * Test execution of the QuartzStatefulScheduledDrivenInitiator handling a 'STOP_ROLLBACK' action.
     * 
     * @throws ResourceException
     * @throws SchedulerException
     */
    @Test
    public void test_successful_ExecuteWithReturnedStopRollbackAction() 
        throws ResourceException, SchedulerException
    {
        QuartzStatefulScheduledDrivenInitiator sdi = setupInitiator();
        
        final Throwable throwable = new RuntimeException();
        
        // set expectations
        classMockery.checking(new Expectations()
        {
            {
                // return 'rollforwardStop' ikasanExceptionAction from the flow invocation
                one(flow).invoke((FlowInvocationContext)with(a(FlowInvocationContext.class)), with(any(Event.class)));
                will(throwException(throwable));
                
                one(exceptionHandler).invoke(with(any(String.class)), with(any(Event.class)), (Throwable)with(equal(throwable)));
                will(returnValue(rollbackStopAction));
            }
        });

        // set common expectations
        this.setEventExpectations();

        setExpectationsForHandleStopAction(false);

        // invoke initiator
       
        AbortTransactionException abortTransactionException = null;
        try{
        	sdi.invoke();
        	fail("AbortTransactionException should have been thrown for rollback scenario");
        } catch(AbortTransactionException exception){
        	abortTransactionException = exception;
        }
        Assert.assertNotNull("AbortTransactionException should have been thrown for rollback scenario",abortTransactionException);
        
        // check initiator is error
        assertTrue(sdi.isError());
    }





    /**
     * Test execution of the QuartzStatefulScheduledDrivenInitiator based on a
     * flow invocation returning a 'ROLLBACK_RETRY' action.
     * 
     * @throws ResourceException
     * @throws SchedulerException
     * 
     * TODO - RJD for #13 change name and description to something like testStartRetryCycle
     */
    @Test    
    public void test_successful_ExecuteWithReturnedRollbackRetryAction() 
        throws ResourceException, SchedulerException
    {
        QuartzStatefulScheduledDrivenInitiator sdi = setupInitiator();
        
        final Throwable throwable = new RuntimeException();
        // set expectations
        classMockery.checking(new Expectations()
        {
            {
                // return 'rollbackRetry' ikasanExceptionAction from the flow invocation
                one(flow).invoke((FlowInvocationContext)with(a(FlowInvocationContext.class)), with(any(Event.class)));
                will(throwException(throwable));
                
                one(exceptionHandler).invoke(with(any(String.class)), with(any(Event.class)), (Throwable)with(equal(throwable)));                
                will(returnValue(rollbackRetryAction));
            }
        });

        // set common expectations
        this.setEventExpectations();


        //expect startRetryCycle to be hit
        setExpectationsForHandleRetryAction(false);

        // invoke initiator
        AbortTransactionException abortTransactionException = null;
        try{
        	sdi.invoke();
        	fail("AbortTransactionException should have been thrown for rollback scenario");
        } catch(AbortTransactionException exception){
        	abortTransactionException = exception;
        }
        Assert.assertNotNull("AbortTransactionException should have been thrown for rollback scenario",abortTransactionException);

    }
    
 
    


    /**
     * Test execution of the QuartzStatefulScheduledDrivenInitiator based on a
     * flow invocation returning a 'ROLLBACK_RETRY' action followed by 
     * a subsequent 'ROLLBACK_RETRY' action.
     * 
     * @throws ResourceException
     * @throws SchedulerException
     */
    @Test
    public void test_successful_RollbackRetryActionFollowedByRollbackRetryAction() 
        throws ResourceException, SchedulerException
    {
        QuartzStatefulScheduledDrivenInitiator scheduledDrivenInitiator = setupInitiator();

        final Throwable throwable = new RuntimeException();
        // first pass
        // set expectations
        classMockery.checking(new Expectations()
        {
            {
                // return 'rollbackRetry' ikasanExceptionAction from the flow invocation
                one(flow).invoke((FlowInvocationContext)with(a(FlowInvocationContext.class)), with(any(Event.class)));
                will(throwException(throwable));
                
                one(exceptionHandler).invoke(with(any(String.class)), with(any(Event.class)), (Throwable)with(equal(throwable)));                
                will(returnValue(rollbackRetryAction));

            }
        });
        setEventExpectations();
        setExpectationsForHandleRetryAction(false);
        
        
        
        Assert.assertNull("Retry count should be null initially", scheduledDrivenInitiator.getRetryCount());
        
        // invoke initiator
        invokeInitiatorExpectingRollback(scheduledDrivenInitiator);
        
        Assert.assertEquals("Retry count should still be 0 before retry has fired for the first time", new Integer(0),scheduledDrivenInitiator.getRetryCount());

        //everything cool so far?
        classMockery.assertIsSatisfied();

        // second pass
        //set up for another invocation - this time we should already be in recovery
        classMockery.checking(new Expectations()
        {
            {
                // return 'rollbackRetry' ikasanExceptionAction from the flow invocation
                one(flow).invoke((FlowInvocationContext)with(a(FlowInvocationContext.class)), with(any(Event.class)));
                will(throwException(throwable));
                
                one(exceptionHandler).invoke(with(any(String.class)), with(any(Event.class)), (Throwable)with(equal(throwable)));                
                will(returnValue(rollbackRetryAction));
            }

        });     
        setEventExpectations();
        setExpectationsForHandleRetryAction(true);
        

        // invoke initiator on retry (recovering)
        invokeInitiatorExpectingRollback(scheduledDrivenInitiator);
        
        Assert.assertEquals("Retry count should 1 after retry has fired for the first time", new Integer(1),scheduledDrivenInitiator.getRetryCount());
    }



    /**
     * Test execution of the QuartzStatefulScheduledDrivenInitiator based on a
     * flow invocation returning a 'ROLLBACK_RETRY' action followed by 
     * a subsequent '*_STOP' action.
     * 
     * @throws ResourceException
     * @throws SchedulerException
     */
    @Test
    public void test_successful_RollbackRetryActionFollowedByAnyStopAction() 
        throws ResourceException, SchedulerException
    {
        QuartzStatefulScheduledDrivenInitiator scheduledDrivenInitiator = setupInitiator();

        final Throwable throwable = new RuntimeException();
        // first pass
        classMockery.checking(new Expectations()
        {
            {
                // return 'rollbackRetry' ikasanExceptionAction from the flow invocation
                one(flow).invoke((FlowInvocationContext)with(a(FlowInvocationContext.class)), with(any(Event.class)));
                will(throwException(throwable));
                
                one(exceptionHandler).invoke(with(any(String.class)), with(any(Event.class)), (Throwable)with(equal(throwable)));                
                will(returnValue(rollbackRetryAction));
            }
        });
        setEventExpectations();
        setExpectationsForHandleRetryAction(false);
        
        
        // invoke initiator
        invokeInitiatorExpectingRollback(scheduledDrivenInitiator);
         
        //everything cool so far?
        classMockery.assertIsSatisfied(); 
        
        // second pass
        classMockery.checking(new Expectations()
        {
            {
                // return 'rollbackRetry' ikasanExceptionAction from the flow invocation
                one(flow).invoke((FlowInvocationContext)with(a(FlowInvocationContext.class)), with(any(Event.class)));
                will(throwException(throwable));
                
                one(exceptionHandler).invoke(with(any(String.class)), with(any(Event.class)), (Throwable)with(equal(throwable)));                
                will(returnValue(rollbackStopAction));
            }
        });
        setEventExpectations();
        setExpectationsForHandleStopAction(true);

        
        // invoke initiator second time
        invokeInitiatorExpectingRollback(scheduledDrivenInitiator);
        
        //check that its now in error
        assertTrue(scheduledDrivenInitiator.isError());
        
        classMockery.assertIsSatisfied();

    }









    /**
     * Test successful external start of an initiator.
     * 
     * @throws SchedulerException
     */
    @Test
    public void test_successful_StartInitiator() 
        throws SchedulerException
    {
        QuartzStatefulScheduledDrivenInitiator scheduleDrivenInitiator = new QuartzStatefulScheduledDrivenInitiator(
                initiatorName,moduleName, eventProvider, flow,
                exceptionHandler);

        // give the initiator a quartz scheduler
        scheduleDrivenInitiator.setScheduler(scheduler);

        //expect it to be started
        setStartInitiatorExpectations(scheduleDrivenInitiator,false);
        
        // invoke start on the initiator
        scheduleDrivenInitiator.start();
    }

    /**
     * Test successful external stop of an initiator.
     * 
     * @throws SchedulerException
     */
    @Test
    public void test_successful_StopInitiator() 
        throws SchedulerException
    {
        QuartzStatefulScheduledDrivenInitiator scheduleDrivenInitiator = new QuartzStatefulScheduledDrivenInitiator(
            initiatorName,moduleName, eventProvider, flow,
            exceptionHandler);
        
        setStopInitiatorExpectations();
        
        setExpectationsForIsRecovering(false);

        // give the initiator a quartz scheduler
        scheduleDrivenInitiator.setScheduler(scheduler);



        // invoke stop on the initiator
        scheduleDrivenInitiator.stop();


    }

 

    /**
     * Teardown after each test
     */
    @After
    public void tearDown()
    {
        // check all expectations were satisfied
        classMockery.assertIsSatisfied();
    }
    
    private void setStopInitiatorExpectations()

    {

        try
        {
            classMockery.checking(new Expectations()
            {
                {
                    // required as part of the stop call on the initiator
                    one(scheduler).pauseJobGroup(with(any(String.class)));
                }
            });
        }
        catch (SchedulerException e)
        {
            throw new RuntimeException(e);
        }
    }
   
    
    /**
     * Usual expectations for the notifyMonitorListener method.
     * 
     * Note that the only aspects of state that we may potentially care about
     * concern the error flag as the rest should be derived.
     * 
     */
    private void setNotifyMonitorListenerExpectations()
    {
        // 
        // set expectations
        classMockery.checking(new Expectations()
        {
            {
                //expectations for getState()
                //assume we are in RUNNING state
                setExpectationsForIsRunning(true);
                setExpectationsForIsRecovering(false);
                one(monitorListener).notify(with(any(String.class)));
            }
        });
    }
    
    private void setExpectationsForIsRecovering(final boolean isRecovering){
        setExpectationsForGetRecoveryTrigger(isRecovering);
    }
    
    private void setExpectationsForGetRecoveryTrigger(final boolean isRecovering){
        final Trigger recoveryTrigger = classMockery.mock(Trigger.class, "recoveryTrigger");
        
        try
        {
            classMockery.checking(new Expectations()
            {
                {
                    
                    
                    one(scheduler).getTrigger("retry_trigger",moduleName+"-"+initiatorName);
                    if (!isRecovering){
                        will(returnValue(null));
                    } else{
                        will(returnValue(recoveryTrigger));
                        allowing(recoveryTrigger).getName();will(returnValue("retry_trigger"));
                        allowing(recoveryTrigger).getGroup();will(returnValue(moduleName+"-"+initiatorName));
                    }
                }
            });
        }
        catch (SchedulerException e)
        {
           throw new RuntimeException(e);
        }
        
    }
    
    
    private void setExpectationsForIsRunning(final boolean isRunning){
        
        final Trigger trigger = classMockery.mock(Trigger.class);
        final Trigger[] triggers = new Trigger[]{trigger};
        final String triggerName = "triggerName";
        final String triggerGroup = "triggerGroup";
        try
        {
            classMockery.checking(new Expectations()
            {
                {
                    one(trigger).getName();will(returnValue(triggerName));
                    one(trigger).getGroup();will(returnValue(triggerGroup));
                    one(scheduler).getTriggerState(triggerName, triggerGroup);
                    if (isRunning){
                        will(returnValue(Trigger.STATE_NORMAL));
                    } else{
                        will(returnValue(Trigger.STATE_PAUSED));
                    }
                 }
            });

            classMockery.checking(new Expectations()
            {
                {
                    one(scheduler).isInStandbyMode();will(returnValue(false));
                    one(scheduler).isShutdown();will(returnValue(false));
                    one(scheduler).getTriggersOfJob("initiatorJob", moduleName+"-"+initiatorName);will(returnValue(triggers));
                }
            });
        }
        catch (SchedulerException e)
        {
           throw new RuntimeException(e);
        }
        
    }

    private void setEventExpectations()
        throws ResourceException
    {
        final List<Event> events = new ArrayList<Event>();
        events.add(event);

        // 
        // set expectations
        classMockery.checking(new Expectations()
        {
            {
                // get events from event provider
                one(eventProvider).getEvents();
                will(returnValue(events));
                // ignore event operations - this is not the test focus
                ignoring(event);
            }
        });
    }

    private void setMultipleEventExpectations(int numOfEvents)
        throws ResourceException
    {
        final List<Event> events = new ArrayList<Event>();
        for(int x=0; x < numOfEvents; x++)
        {
            events.add(event);
        }
    
        // 
        // set expectations
        classMockery.checking(new Expectations()
        {
            {
                // get events from event provider
                one(eventProvider).getEvents();
                will(returnValue(events));
                // ignore event operations - this is not the test focus
                ignoring(event);
            }
        });
    }
    
    private void setExpectationsForResume(boolean isRecovering)
    {
        //expectations for handleNullAction
        setExpectationsForIsRecovering(isRecovering);
        if (isRecovering){
            setCancelRetryCycleExpectations(isRecovering);
            
            //setStartInitiatorExpectations(true);
            try
            {
                classMockery.checking(new Expectations()
                {
                    {
                exactly(1).of(scheduler).resumeJobGroup(with(equal(moduleName+"-"+initiatorName)));
                    }
                });
            }
            catch (SchedulerException e)
            {
                throw new RuntimeException(e);
            }
        }
        
    }
    

    
    private void setExpectationsForHandleStopAction(boolean isRecovering)
    {
        //expectations for handleStopAction
        setExpectationsForIsRecovering(isRecovering);

        if (isRecovering){
            setCancelRetryCycleExpectations(isRecovering);
        }
        setStopInitiatorExpectations();
        setNotifyMonitorListenerExpectations();
        
    }
    
    private void setExpectationsForHandleRetryAction(boolean isRecovering)
    {
        //expectations for handleRetryAction
        setExpectationsForIsRecovering(isRecovering);
        if (!isRecovering){
            setStartRetryCycleExpectations();
            setNotifyMonitorListenerExpectations();
        }
        
    }
    
    private void setStartRetryCycleExpectations()
    {
        // set expectations
        try
        {
            classMockery.checking(new Expectations()
            {
                {
                    // we must stop the scheduled triggers on this job on group name            
                    one(scheduler).pauseJobGroup(with(equal(moduleName+"-"+initiatorName))); 
                     
                    // and then start retry trigger
                    //TODO - can we match the retry trigger a little tighter                    
                    one(scheduler).scheduleJob(with(any(Trigger.class)));
                }
            });
        }
        catch (SchedulerException e)
        {
            throw new RuntimeException(e);
        }
    }
    
    
    private void invokeInitiatorExpectingRollback(QuartzStatefulScheduledDrivenInitiator scheduledDrivenInitiator)
    {
        AbortTransactionException abortTransactionException = null;
        try
        {
            scheduledDrivenInitiator.invoke();
            fail("exception should have been thrown");
        }
        catch(AbortTransactionException e)
        {
            abortTransactionException = e;
        }
        Assert.assertNotNull("exception should have been thrown",abortTransactionException);
    }

    private QuartzStatefulScheduledDrivenInitiator setupInitiator()
    {
        QuartzStatefulScheduledDrivenInitiator sdi = new QuartzStatefulScheduledDrivenInitiator(
                initiatorName,moduleName, eventProvider, flow,
                exceptionHandler);

        // give the initiator a quartz scheduler
        sdi.setScheduler(scheduler);

        // add a monitor
        sdi.addListener(monitorListener);
        
        return sdi;
    }
    
    private void setCancelRetryCycleExpectations(final boolean isRecovering)
    {
        
        setExpectationsForGetRecoveryTrigger(isRecovering);
        
        // set expectations
        try
        {
            classMockery.checking(new Expectations()
            {
                {
                    if (isRecovering){
                    // unschedule recovery trigger
                    one(scheduler).unscheduleJob(with(equal("retry_trigger")), with(equal(moduleName+"-"+initiatorName)));
                    }
                }
            });
        }
        catch (SchedulerException e)
        {
           throw new RuntimeException(e);
        }
    }



    private void setStartInitiatorExpectations(QuartzStatefulScheduledDrivenInitiator scheduleDrivenInitiator, final boolean previouslyStarted)
    {
        final Trigger businessAsUsualTrigger1 = classMockery.mock(Trigger.class, "businessAsUsualTrigger1");
        final Trigger businessAsUsualTrigger2 = classMockery.mock(Trigger.class, "businessAsUsualTrigger2");
        
        List<Trigger> triggers = new ArrayList<Trigger>();
        triggers.add(businessAsUsualTrigger1);
        triggers.add(businessAsUsualTrigger2);
        scheduleDrivenInitiator.setTriggers(triggers);
        
        try
        {
            classMockery.checking(new Expectations()
            {
                {
                    one(scheduler).getTriggersOfJob("initiatorJob", moduleName+"-"+initiatorName);
                    if (previouslyStarted){
                        will(returnValue(new Trigger[]{businessAsUsualTrigger1, businessAsUsualTrigger2}));
                    } else{
                        will(returnValue(new Trigger[]{}));
                        
                        //first trigger
                        one(businessAsUsualTrigger1).setGroup(moduleName+"-"+initiatorName);
                        one(scheduler).scheduleJob((JobDetail)with(any(JobDetail.class)), with(equal(businessAsUsualTrigger1)));
                        
                        //subsequent trigger
                        one(businessAsUsualTrigger2).setGroup(moduleName+"-"+initiatorName);
                        one(businessAsUsualTrigger2).setJobGroup(moduleName+"-"+initiatorName);
                        one(businessAsUsualTrigger2).setJobName("initiatorJob");
                        one(scheduler).scheduleJob(businessAsUsualTrigger2 );
                    }
                    
                    
                    
                    // resume the flow
                    exactly(1).of(scheduler).resumeJobGroup(with(equal(moduleName+"-"+initiatorName)));
                }
            });
        }
        catch (SchedulerException e)
        {
            throw new RuntimeException(e);
        }
    }
}
