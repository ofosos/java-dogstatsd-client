package com.github.arnabk.statsd;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

import org.apache.commons.math3.util.Precision;

import com.timgroup.statsd.StatsDClient;
import com.timgroup.statsd.StatsDClientErrorHandler;
import com.timgroup.statsd.StatsDClientException;

/**
 * A simple StatsD client implementation facilitating metrics recording.
 * 
 * <p>Upon instantiation, this client will establish a socket connection to a StatsD instance
 * running on the specified host and port. Metrics are then sent over this connection as they are
 * received by the client.
 * </p>
 * 
 * <p>Three key methods are provided for the submission of data-points for the application under
 * scrutiny:
 * <ul>
 *   <li>{@link #incrementCounter} - adds one to the value of the specified named counter</li>
 *   <li>{@link #recordGaugeValue} - records the latest fixed value for the specified named gauge</li>
 *   <li>{@link #recordExecutionTime} - records an execution time in milliseconds for the specified named operation</li>
 *   <li>{@link #recordHistogramValue} - records a value, to be tracked with average, maximum, and percentiles</li>
 * </ul>
 * </p>
 * 
 * <p>As part of a clean system shutdown, the {@link #stop()} method should be invoked
 * on any StatsD clients.</p>
 * 
 * <p>This class is a blocking implementation. It is preferable to use with already existing threading systems or logging systems like slf4j(log4j implementation)</p>
 * 
 * @author Arnab 
 *
 */
public class BlockingStatsDClient implements StatsDClient {

	protected static final StatsDClientErrorHandler NO_OP_HANDLER = new StatsDClientErrorHandler() {
        @Override public void handle(Exception e) { /* No-op */ }
    };

    protected final String prefix;
    protected final DatagramSocket clientSocket;
    protected final StatsDClientErrorHandler handler;
    protected final String[] constantTags;

    /**
     * Create a new StatsD client communicating with a StatsD instance on the
     * specified host and port. All messages send via this client will have
     * their keys prefixed with the specified string. The new client will
     * attempt to open a connection to the StatsD server immediately upon
     * instantiation, and may throw an exception if that a connection cannot
     * be established. Once a client has been instantiated in this way, all
     * exceptions thrown during subsequent usage are consumed, guaranteeing
     * that failures in metrics will not affect normal code execution.
     *
     * @param prefix
     *     the prefix to apply to keys sent via this client
     * @param hostname
     *     the host name of the targeted StatsD server
     * @param port
     *     the port of the targeted StatsD server
     * @throws StatsDClientException
     *     if the client could not be started
     */
    public BlockingStatsDClient(String prefix, String hostname, int port) throws StatsDClientException {
        this(prefix, hostname, port, null, NO_OP_HANDLER);
    }

    /**
     * Create a new StatsD client communicating with a StatsD instance on the
     * specified host and port. All messages send via this client will have
     * their keys prefixed with the specified string. The new client will
     * attempt to open a connection to the StatsD server immediately upon
     * instantiation, and may throw an exception if that a connection cannot
     * be established. Once a client has been instantiated in this way, all
     * exceptions thrown during subsequent usage are consumed, guaranteeing
     * that failures in metrics will not affect normal code execution.
     * 
     * @param prefix
     *     the prefix to apply to keys sent via this client
     * @param hostname
     *     the host name of the targeted StatsD server
     * @param port
     *     the port of the targeted StatsD server
     * @param constantTags
     *     tags to be added to all content sent
     * @throws StatsDClientException
     *     if the client could not be started
     */
    public BlockingStatsDClient(String prefix, String hostname, int port, String[] constantTags) throws StatsDClientException {
        this(prefix, hostname, port, constantTags, NO_OP_HANDLER);
    }

    /**
     * Create a new StatsD client communicating with a StatsD instance on the
     * specified host and port. All messages send via this client will have
     * their keys prefixed with the specified string. The new client will
     * attempt to open a connection to the StatsD server immediately upon
     * instantiation, and may throw an exception if that a connection cannot
     * be established. Once a client has been instantiated in this way, all
     * exceptions thrown during subsequent usage are passed to the specified
     * handler and then consumed, guaranteeing that failures in metrics will
     * not affect normal code execution.
     * 
     * @param prefix
     *     the prefix to apply to keys sent via this client
     * @param hostname
     *     the host name of the targeted StatsD server
     * @param port
     *     the port of the targeted StatsD server
     * @param constantTags
     *     tags to be added to all content sent
     * @param errorHandler
     *     handler to use when an exception occurs during usage
     * @throws StatsDClientException
     *     if the client could not be started
     */
    public BlockingStatsDClient(String prefix, String hostname, int port, String[] constantTags, StatsDClientErrorHandler errorHandler) throws StatsDClientException {
        if(prefix != null && prefix.length() > 0) {
            this.prefix = String.format("%s.", prefix);
        } else {
            this.prefix = "";
        }
        this.handler = errorHandler;
        if(constantTags != null && constantTags.length == 0) {
            constantTags = null;
        }
        this.constantTags = constantTags;

        try {
            this.clientSocket = new DatagramSocket();
            this.clientSocket.connect(new InetSocketAddress(hostname, port));
        } catch (Exception e) {
            throw new StatsDClientException("Failed to start StatsD client", e);
        }
    }

    /**
     * Cleanly shut down this StatsD client. This method may throw an exception if
     * the socket cannot be closed.
     */
    @Override
    public void stop() {
    	try {
            if (clientSocket != null) {
                clientSocket.close();
            }
        }
        catch (Exception e) {
            handler.handle(e);
        }
        finally {
        }
    }

    /**
     * Generate a suffix conveying the given tag list to the client
     */
    String tagString(String[] tags) {
        boolean have_call_tags = (tags != null && tags.length > 0);
        boolean have_constant_tags = (constantTags != null && constantTags.length > 0);
        if(!have_call_tags && !have_constant_tags) {
            return "";
        }
        StringBuilder sb = new StringBuilder("|#");
        if(have_constant_tags) {
            for(int n=constantTags.length - 1; n>=0; n--) {
                sb.append(constantTags[n]);
                if(n > 0 || have_call_tags) {
                    sb.append(",");
                }
            }
        }
        if (have_call_tags) {
            for(int n=tags.length - 1; n>=0; n--) {
                sb.append(tags[n]);
                if(n > 0) {
                    sb.append(",");
                }
            }
        }
        return sb.toString();
    }

    /**
     * Adjusts the specified counter by a given delta.
     * 
     * 
     * @param aspect
     *     the name of the counter to adjust
     * @param delta
     *     the amount to adjust the counter by
     * @param tags
     *     array of tags to be added to the data
     */
    @Override
    public void count(String aspect, long delta, String... tags) {
    	blockingSend(String.format("%s%s:%d|c%s", prefix, aspect, delta, tagString(tags)));
    }
    
    public void count(String aspect, long delta, double sampleRate, String... tags) {
    	if(isInvalidSample(sampleRate)) {
    		return;
    	}
    	blockingSend(String.format("%s%s:%d|c|%f%s", prefix, aspect, delta, sampleRate, tagString(tags)));
    }

    /**
     * Increments the specified counter by one.
     * 
     * 
     * @param aspect
     *     the name of the counter to increment
     * @param tags
     *     array of tags to be added to the data
     */
    @Override
    public void incrementCounter(String aspect, String... tags) {
        count(aspect, 1, tags);
    }
    
    public void incrementCounter(String aspect, double sampleRate, String... tags) {
        count(aspect, 1, sampleRate, tags);
    }

    /**
     * Convenience method equivalent to {@link #incrementCounter(String, String[])}. 
     */
    @Override
    public void increment(String aspect, String... tags) {
        incrementCounter(aspect, tags);
    }
    
    public void increment(String aspect, double sampleRate, String... tags) {
        incrementCounter(aspect, sampleRate, tags);
    }

    /**
     * Decrements the specified counter by one.
     * 
     * 
     * @param aspect
     *     the name of the counter to decrement
     * @param tags
     *     array of tags to be added to the data
     */
    @Override
    public void decrementCounter(String aspect, String... tags) {
        count(aspect, -1, tags);
    }
    
    public void decrementCounter(String aspect, double sampleRate, String... tags) {
        count(aspect, -1, sampleRate, tags);
    }

    /**
     * Convenience method equivalent to {@link #decrementCounter(String, String[])}. 
     */
    @Override
    public void decrement(String aspect, String... tags) {
        decrementCounter(aspect, tags);
    }
    
    public void decrement(String aspect, double sampleRate, String... tags) {
        decrementCounter(aspect, sampleRate, tags);
    }

    /**
     * Records the latest fixed value for the specified named gauge.
     * 
     * 
     * @param aspect
     *     the name of the gauge
     * @param value
     *     the new reading of the gauge
     * @param tags
     *     array of tags to be added to the data
     */
    @Override
    public void recordGaugeValue(String aspect, double value, String... tags) {
    	blockingSend(String.format("%s%s:%f|g%s", prefix, aspect, Precision.round(value, 6), tagString(tags)));
    }
    
    public void recordGaugeValue(String aspect, double value, double sampleRate, String... tags) {
    	if(isInvalidSample(sampleRate)) {
    		return;
    	}
    	blockingSend(String.format("%s%s:%f|g|%f%s", prefix, aspect, Precision.round(value, 6), sampleRate, tagString(tags)));
    }

    /**
     * Convenience method equivalent to {@link #recordGaugeValue(String, double, String[])}.
     */
    @Override
    public void gauge(String aspect, double value, String... tags) {
        recordGaugeValue(aspect, value, tags);
    }
    
    public void gauge(String aspect, double value, double sampleRate, String... tags) {
        recordGaugeValue(aspect, value, sampleRate, tags);
    }

    /**
     * Records the latest fixed value for the specified named gauge.
     * 
     * 
     * @param aspect
     *     the name of the gauge
     * @param value
     *     the new reading of the gauge
     * @param tags
     *     array of tags to be added to the data
     */
    @Override
    public void recordGaugeValue(String aspect, long value, String... tags) {
    	blockingSend(String.format("%s%s:%d|g%s", prefix, aspect, value, tagString(tags)));
    }
    
    public void recordGaugeValue(String aspect, long value, double sampleRate, String... tags) {
    	if(isInvalidSample(sampleRate)) {
    		return;
    	}
    	blockingSend(String.format("%s%s:%d|g|%f%s", prefix, aspect, value, sampleRate, tagString(tags)));
    }

    /**
     * Convenience method equivalent to {@link #recordGaugeValue(String, int, String[])}. 
     */
    @Override
    public void gauge(String aspect, long value, String... tags) {
        recordGaugeValue(aspect, value, tags);
    }
    
    public void gauge(String aspect, long value, double sampleRate, String... tags) {
        recordGaugeValue(aspect, value, sampleRate, tags);
    }

    /**
     * Records an execution time in milliseconds for the specified named operation.
     * 
     * 
     * @param aspect
     *     the name of the timed operation
     * @param timeInMs
     *     the time in milliseconds
     * @param tags
     *     array of tags to be added to the data
     */
    @Override
    public void recordExecutionTime(String aspect, long timeInMs, String... tags) {
        blockingSend(String.format("%s%s:%d|ms%s", prefix, aspect, timeInMs, tagString(tags)));
    }
    
    public void recordExecutionTime(String aspect, long timeInMs, double sampleRate, String... tags) {
    	if(isInvalidSample(sampleRate)) {
    		return;
    	}
        blockingSend(String.format("%s%s:%d|ms|%f%s", prefix, aspect, timeInMs, sampleRate, tagString(tags)));
    }

    /**
     * Convenience method equivalent to {@link #recordExecutionTime(String, long, String[])}.
     */
    @Override
    public void time(String aspect, long value, String... tags) {
        recordExecutionTime(aspect, value, tags);
    }
    
    public void time(String aspect, long value, double sampleRate, String... tags) {
        recordExecutionTime(aspect, value, sampleRate, tags);
    }

    /**
     * Records a value for the specified named histogram.
     *
     *
     * @param aspect
     *     the name of the histogram
     * @param value
     *     the value to be incorporated in the histogram
     * @param tags
     *     array of tags to be added to the data
     */
    @Override
    public void recordHistogramValue(String aspect, double value, String... tags) {
    	blockingSend(String.format("%s%s:%f|h%s", prefix, aspect, Precision.round(value, 6), tagString(tags)));
    }
    
    public void recordHistogramValue(String aspect, double value, double sampleRate, String... tags) {
    	if(isInvalidSample(sampleRate)) {
    		return;
    	}
    	blockingSend(String.format("%s%s:%f|h|%f%s", prefix, aspect, Precision.round(value, 6), sampleRate, tagString(tags)));
    }

    /**
     * Convenience method equivalent to {@link #recordHistogramValue(String, double, String[])}.
     */
    @Override
    public void histogram(String aspect, double value, String... tags) {
        recordHistogramValue(aspect, value, tags);
    }
    
    public void histogram(String aspect, double value, double sampleRate, String... tags) {
        recordHistogramValue(aspect, value, sampleRate, tags);
    }

    /**
     * Records a value for the specified named histogram.
     * 
     * 
     * @param aspect
     *     the name of the histogram
     * @param value
     *     the value to be incorporated in the histogram
     * @param tags
     *     array of tags to be added to the data
     */
    @Override
    public void recordHistogramValue(String aspect, long value, String... tags) {
    	blockingSend(String.format("%s%s:%d|h%s", prefix, aspect, value, tagString(tags)));
    }
    
    public void recordHistogramValue(String aspect, long value, double sampleRate, String... tags) {
    	if(isInvalidSample(sampleRate)) {
    		return;
    	}
    	blockingSend(String.format("%s%s:%d|h|%f%s", prefix, aspect, value, sampleRate, tagString(tags)));
    }

    /**
     * Convenience method equivalent to {@link #recordHistogramValue(String, int, String[])}. 
     */
    @Override
    public void histogram(String aspect, long value, String... tags) {
        recordHistogramValue(aspect, value, tags);
    }
    
    public void histogram(String aspect, long value, double sampleRate, String... tags) {
        recordHistogramValue(aspect, value, sampleRate, tags);
    }
    
    private boolean isInvalidSample(double sampleRate) {
    	return sampleRate != 1 && Math.random() > sampleRate;
    }

    private void blockingSend(String message) {
        try {
            final byte[] sendData = message.getBytes();
            final DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length);
            clientSocket.send(sendPacket);
        } catch (Exception e) {
            handler.handle(e);
        }
    }
}
