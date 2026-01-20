#!/usr/bin/python3

import matplotlib.pyplot as plt
from matplotlib.animation import FuncAnimation, PillowWriter
import numpy as np
import random

# Vehicle names
vehicles = ["VW Golf mit Allrad", "N Polo", "Audi A3"]

MAX_VEHICLES = 3
NUM_FRAMES = 10

# Initialize vehicle states
vehicle_states = []

# For reproducibility
random.seed(42)

fig, ax = plt.subplots()
ax.set_aspect("equal", adjustable="box")
ax.set_xlim(-10, 10)
ax.set_ylim(-10, 10)
ax.axhline(0)
ax.axvline(0)
ax.set_xlabel("X")
ax.set_ylabel("Y")

scatter = ax.scatter([], [])
texts = []

# Helper: check if a position overlaps an existing vehicle (tolerance for float coords)
def find_collision(x, y, tol=0.5):
    for i, v in enumerate(vehicle_states):
        if abs(v["x"] - x) < tol and abs(v["y"] - y) < tol:
            return i
    return None

def update(frame):
    global vehicle_states, texts

    for t in texts:
        t.remove()
    texts = []

    new_states = []

    # Move vehicles
    for v in vehicle_states:
        dx, dy = np.random.uniform(-1, 1, 2)
        new_x = v["x"] + dx
        new_y = v["y"] + dy

        collision_idx = find_collision(new_x, new_y)
        if collision_idx is not None:
            # Remove the collided vehicle
            # print(f"{v['name']} removes {vehicle_states[collision_idx]['name']}")
            if collision_idx < len(new_states):
                new_states.pop(collision_idx)
            else:
                vehicle_states.pop(collision_idx)

        new_states.append({
            "name": v["name"],
            "x": new_x,
            "y": new_y
        })

    vehicle_states = new_states

    # Possibly add a new vehicle if less than MAX_VEHICLES
    if len(vehicle_states) < MAX_VEHICLES and random.random() < 0.5:
        existing_names = {v["name"] for v in vehicle_states}
        candidates = [v for v in vehicles if v not in existing_names]
        if candidates:
            name = random.choice(candidates)
            vehicle_states.append({
                "name": name,
                "x": random.uniform(-8, 8),
                "y": random.uniform(-8, 8)
            })

    xs = [v["x"] for v in vehicle_states]
    ys = [v["y"] for v in vehicle_states]
    scatter.set_offsets(np.column_stack([xs, ys]))

    for v in vehicle_states:
        t = ax.text(v["x"], v["y"] + 0.3, v["name"], fontsize=8, ha="center")
        texts.append(t)

    ax.set_title(f"Frame {frame+1}")
    return scatter, *texts

ani = FuncAnimation(
    fig,
    update,
    frames=NUM_FRAMES,
    interval=500,
    repeat=True
)

writer = PillowWriter(fps=2)
ani.save("vehicle_collision_animation.gif", writer=writer)
print("Saved GIF as vehicle_collision_animation.gif")
