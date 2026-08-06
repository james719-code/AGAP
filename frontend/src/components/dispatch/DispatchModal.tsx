import React, { useState } from 'react';
import { X, ShieldAlert, UserCheck, MapPin, Truck, AlertTriangle } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';

interface DispatchModalProps {
  isOpen: boolean;
  onClose: () => void;
  incidentId: string | number;
  emergencyType?: string;
  location?: string;
  onDispatchSuccess?: (unitName: string) => void;
}

export const DispatchModal: React.FC<DispatchModalProps> = ({
  isOpen,
  onClose,
  incidentId,
  emergencyType = 'MEDICAL EMERGENCY',
  location = 'Legazpi City Center',
  onDispatchSuccess,
}) => {
  const [selectedUnit, setSelectedUnit] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  if (!isOpen) return null;

  const mockAvailableUnits = [
    { id: 'RES-01', callsign: 'ALBAY-MEDIC-1', type: 'AMBULANCE', agency: 'BRHMC Rescue', distance: '0.8 km', eta: '3 mins', status: 'IDLE' },
    { id: 'RES-02', callsign: 'BFP-ENGINE-4', type: 'FIRE TRUCK', agency: 'BFP Legazpi Station', distance: '1.4 km', eta: '5 mins', status: 'IDLE' },
    { id: 'RES-03', callsign: 'PNP-PATROL-12', type: 'POLICE CRUSER', agency: 'Legazpi City Police', distance: '2.1 km', eta: '7 mins', status: 'IDLE' },
    { id: 'RES-04', callsign: 'DRRMO-RESCUE-02', type: 'RESCUE TRUCK', agency: 'Albay DRRMO', distance: '3.0 km', eta: '10 mins', status: 'IDLE' },
  ];

  const handleDispatch = () => {
    if (!selectedUnit) return;
    setIsSubmitting(true);
    setTimeout(() => {
      setIsSubmitting(false);
      if (onDispatchSuccess) {
        onDispatchSuccess(selectedUnit);
      }
      onClose();
    }, 600);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/80 backdrop-blur-sm p-4">
      <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-2xl max-w-lg w-full p-6 shadow-2xl space-y-5 animate-in fade-in zoom-in-95 duration-150">
        {/* Header */}
        <div className="flex items-center justify-between pb-3 border-b border-slate-200 dark:border-slate-800">
          <div className="flex items-center gap-2.5">
            <div className="bg-red-500/10 p-2 rounded-xl border border-red-500/20 text-red-500">
              <ShieldAlert className="h-5 w-5" />
            </div>
            <div>
              <h3 className="font-extrabold text-base text-slate-900 dark:text-slate-100">
                DISPATCH RESPONDER UNIT
              </h3>
              <p className="text-xs text-slate-500 dark:text-slate-400 font-mono">
                Incident #{incidentId} • {emergencyType}
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1 rounded-lg text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Location banner */}
        <div className="bg-slate-50 dark:bg-slate-950 p-3 rounded-xl border border-slate-200 dark:border-slate-800 flex items-center justify-between text-xs">
          <div className="flex items-center gap-2 text-slate-600 dark:text-slate-300">
            <MapPin className="h-4 w-4 text-red-500 shrink-0" />
            <span className="font-semibold">{location}</span>
          </div>
          <Badge variant="outline" className="text-[10px] border-amber-500/30 text-amber-600 dark:text-amber-400 bg-amber-500/10">
            <AlertTriangle className="h-3 w-3 mr-1" /> UNASSIGNED
          </Badge>
        </div>

        {/* Available Roster */}
        <div className="space-y-2">
          <label className="text-xs font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">
            Select Available Response Unit ({mockAvailableUnits.length})
          </label>
          <div className="space-y-2 max-h-60 overflow-y-auto pr-1">
            {mockAvailableUnits.map((unit) => {
              const isSelected = selectedUnit === unit.callsign;
              return (
                <div
                  key={unit.id}
                  onClick={() => setSelectedUnit(unit.callsign)}
                  className={`p-3 rounded-xl border cursor-pointer transition-all flex items-center justify-between ${
                    isSelected
                      ? 'bg-red-500/10 border-red-500 text-slate-900 dark:text-slate-100 shadow-sm'
                      : 'bg-white dark:bg-slate-950 border-slate-200 dark:border-slate-800/80 text-slate-700 dark:text-slate-300 hover:border-slate-300 dark:hover:border-slate-700'
                  }`}
                >
                  <div className="flex items-center gap-3">
                    <div className={`p-2 rounded-lg ${isSelected ? 'bg-red-500 text-white' : 'bg-slate-100 dark:bg-slate-800 text-slate-500'}`}>
                      <Truck className="h-4 w-4" />
                    </div>
                    <div>
                      <div className="text-xs font-bold">{unit.callsign}</div>
                      <div className="text-[11px] text-slate-500 dark:text-slate-400">{unit.agency} • {unit.type}</div>
                    </div>
                  </div>
                  <div className="text-right">
                    <div className="text-xs font-mono font-bold text-teal-600 dark:text-teal-400">{unit.eta}</div>
                    <div className="text-[10px] text-slate-400">{unit.distance}</div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Action Buttons */}
        <div className="flex items-center justify-end gap-3 pt-2">
          <Button variant="outline" size="sm" onClick={onClose} className="border-slate-300 dark:border-slate-800 text-xs">
            Cancel
          </Button>
          <Button
            onClick={handleDispatch}
            disabled={!selectedUnit || isSubmitting}
            className="bg-red-600 hover:bg-red-500 text-white text-xs font-bold px-5 h-9 rounded-xl shadow-md flex items-center gap-1.5"
          >
            <UserCheck className="h-4 w-4" />
            {isSubmitting ? 'Dispatching...' : `Dispatch ${selectedUnit || 'Unit'}`}
          </Button>
        </div>
      </div>
    </div>
  );
};
