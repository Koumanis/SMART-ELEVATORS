const API_URL = "http://localhost:8080/api";
const TOTAL_FLOORS = 200; // 0 έως 199

const connectionPill = document.getElementById("connection-pill");
const pillLabel = connectionPill.querySelector(".indicator-label");
const shaftsContainer = document.getElementById("shafts-container");
const telemetryTable = document.getElementById("telemetry-table");
const callForm = document.getElementById("call-form");
const consoleLog = document.getElementById("call-console-log");
const activeUnitsCount = document.getElementById("active-units-count");

// Αν δεν λάβουμε 3 ασανσέρ από το backend, διαμορφώνουμε fallback 3 μονάδες
function normalizeElevators(data) {
  if (Array.isArray(data) && data.length > 0) {
    if (data.length === 3) return data;
    // Εάν ο backend έχει μόνο 1 ασανσέρ (π.χ. E1), συμπληρώνουμε δοκιμαστικά E2 και E3
    const result = [...data];
    while (result.length < 3) {
      result.push({
        id: `E${result.length + 1}`,
        currentFloor: 0,
        direction: "IDLE"
      });
    }
    return result.slice(0, 3);
  }
  return [
    { id: "E1", currentFloor: 0, direction: "IDLE" },
    { id: "E2", currentFloor: 0, direction: "IDLE" },
    { id: "E3", currentFloor: 0, direction: "IDLE" }
  ];
}

async function fetchTelemetry() {
  try {
    const res = await fetch(`${API_URL}/elevators`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);

    const rawData = await res.json();
    const elevators = normalizeElevators(rawData);

    setOnline(true);
    activeUnitsCount.textContent = elevators.length;
    renderShafts(elevators);
    renderTelemetryTable(elevators);

  } catch (err) {
    setOnline(false);
  }
}

function setOnline(isOnline) {
  if (isOnline) {
    connectionPill.className = "status-indicator online";
    pillLabel.textContent = "SYSTEM ONLINE";
  } else {
    connectionPill.className = "status-indicator offline";
    pillLabel.textContent = "SERVER UNREACHABLE";
  }
}

function renderShafts(elevators) {
  shaftsContainer.innerHTML = "";

  elevators.forEach((elev) => {
    const floor = Math.max(0, Math.min(elev.currentFloor ?? 0, TOTAL_FLOORS - 1));
    // Υπολογισμός θέσης bottom σε ποσοστό (0% έως ~94% για να μην εξέχει ο θάλαμος)
    const positionPercent = (floor / (TOTAL_FLOORS - 1)) * 92;

    const shaftCol = document.createElement("div");
    shaftCol.className = "shaft-column";

    shaftCol.innerHTML = `
      <div class="shaft-head">
        <span class="shaft-name">SHAFT // ${elev.id || 'E'}</span>
        <span class="shaft-floor-badge">FL ${String(floor).padStart(3, '0')}</span>
      </div>
      <div class="shaft-track">
        <div class="elevator-car" style="bottom: ${positionPercent}%;">
          <span class="car-state-icon">${getDirectionGlyph(elev.direction)}</span>
          <span class="car-floor-indicator">${floor}</span>
        </div>
      </div>
    `;

    shaftsContainer.appendChild(shaftCol);
  });
}

function getDirectionGlyph(dir) {
  if (!dir) return "●";
  const upper = dir.toUpperCase();
  if (upper.includes("UP")) return "▲ UP";
  if (upper.includes("DOWN")) return "▼ DN";
  return "● IDLE";
}

function renderTelemetryTable(elevators) {
  telemetryTable.innerHTML = "";

  elevators.forEach(elev => {
    const row = document.createElement("div");
    row.className = "telemetry-row";
    row.innerHTML = `
      <span class="telemetry-name">UNIT ${elev.id}</span>
      <span class="telemetry-data">FL ${elev.currentFloor ?? 0}</span>
      <span class="telemetry-status">${elev.direction || "IDLE"}</span>
    `;
    telemetryTable.appendChild(row);
  });
}

// Dispatch Form Submission
callForm.addEventListener("submit", async (e) => {
  e.preventDefault();

  const sourceFloor = parseInt(document.getElementById("source-floor").value, 10);
  const destFloor = parseInt(document.getElementById("dest-floor").value, 10);

  if (sourceFloor === destFloor) {
    consoleLog.textContent = "ERR: Source and target floor must differ.";
    consoleLog.style.color = "var(--accent-amber)";
    return;
  }

  consoleLog.textContent = `Transmitting dispatch ${sourceFloor} -> ${destFloor}...`;
  consoleLog.style.color = "var(--text-medium)";

  try {
    const res = await fetch(`${API_URL}/calls`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ sourceFloor, destinationFloor: destFloor })
    });

    if (res.ok) {
      consoleLog.textContent = `ACK: Dispatch assigned [${sourceFloor} -> ${destFloor}].`;
      consoleLog.style.color = "var(--accent-green)";
      fetchTelemetry();
    } else {
      consoleLog.textContent = `NACK: Server rejected with code ${res.status}`;
      consoleLog.style.color = "var(--accent-rose)";
    }
  } catch (err) {
    consoleLog.textContent = "ERR: Dispatch failure. REST endpoint unreachable.";
    consoleLog.style.color = "var(--accent-rose)";
  }
});

// Real-time loop
fetchTelemetry();
setInterval(fetchTelemetry, 1000);