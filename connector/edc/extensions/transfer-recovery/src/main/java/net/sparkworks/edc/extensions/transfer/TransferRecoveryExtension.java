package net.sparkworks.edc.extensions.transfer;

import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.connector.dataplane.spi.manager.DataPlaneManager;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;
import org.eclipse.edc.spi.types.domain.transfer.FlowType;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Extension that recovers and resumes transfer processes after connector restart
 */
@Extension(value = "Transfer Process Recovery Extension")
public class TransferRecoveryExtension implements ServiceExtension {
    
    @Inject
    private TransferProcessStore transferProcessStore;
    @Inject
    private DataPlaneManager dataPlaneManager;
    
    @Override
    public String name() {
        return "Transfer Process Recovery";
    }
    
    @Override
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor();
        
        monitor.info("=====================================================");
        monitor.info("Initializing Transfer Process Recovery Extension");
        monitor.info("=====================================================");
        
        // Schedule recovery check after a delay (to ensure all services are ready)
        var executor = Executors.newSingleThreadScheduledExecutor();
        executor.schedule(() -> {
            recoverTransferProcesses(monitor);
        }, 10, TimeUnit.SECONDS);
    }
    
    private void recoverTransferProcesses(org.eclipse.edc.spi.monitor.Monitor monitor) {
        monitor.info("Checking for transfer processes to recover...");
        
        try {
            // Get ALL transfer processes (no filter)
            var querySpec = QuerySpec.Builder.newInstance().build();
            var allTransfers = transferProcessStore.findAll(querySpec);
            
            int count = 0;
            
            allTransfers.forEach((transfer) -> {
                int state = transfer.getState();
                
                if (state == TransferProcessStates.STARTED.code()) {
                    try {
                        // CRITICAL: Manually trigger the data plane transfer
                        // This will recreate the streaming connection
                        
                        // Option 1: Force re-initiation through the data plane
                        var dataFlowRequest = createDataFlowRequest(transfer);
                        if (dataFlowRequest != null) {
                            monitor.info("  Initiating data plane transfer...");
                            dataPlaneManager.start(dataFlowRequest);
                            monitor.info("  ✓ Data plane transfer initiated");
                        }
                        
                        // Update the transfer state
                        transfer.transitionStarted();
                        transferProcessStore.save(transfer);
                        
                        monitor.warning("  ✓ Transfer recovered and restarted");
                        
                    } catch (Exception e) {
                        monitor.severe("  ✗ Failed to recover transfer: " + transfer.getId(), e);
                    }
                }
            });
            //
            //            if (count > 0) {
            //                monitor.info("=====================================================");
            //                monitor.info("Recovered " + count + " transfer process(es)");
            //                monitor.info("Transfer manager will resume streaming");
            //                monitor.info("=====================================================");
            //            } else {
            //                monitor.info("No active transfer processes found to recover");
            //            }
            
        } catch (Exception e) {
            monitor.severe("Failed to recover transfer processes", e);
        }
    }
    
    private DataFlowStartMessage createDataFlowRequest(TransferProcess transfer) {
        try {
            return DataFlowStartMessage.Builder.newInstance().id(transfer.getId()).processId(transfer.getCorrelationId()).sourceDataAddress(transfer.getContentDataAddress()).destinationDataAddress(transfer.getDataDestination()).participantId(transfer.getCounterPartyAddress()).agreementId(transfer.getContractId()).assetId(transfer.getAssetId()).flowType(FlowType.PUSH)
                    //                    .properties(transfer.getPrivateProperties())
                    .build();
        } catch (Exception e) {
            return null;
        }
    }
}
